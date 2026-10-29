package fr.darkvodou.facets.symmetry;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.extent.filter.block.ExtentFilterBlock;
import com.fastasyncworldedit.core.queue.Filter;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.fastasyncworldedit.core.util.ExtentTraverser;
import com.fastasyncworldedit.core.util.task.InterruptableCondition;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import fr.darkvodou.facets.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Copies every block an edit sets to its symmetric positions. Sits above FAWE's history, so the copies are undone
 * with the edit, and below nothing: the copies are not copied again.
 * <p>
 * FAWE's fast paths (whole regions at once) skip setBlock, so they are done here block by block.
 * <p>
 * The copies are not written as they come. FAWE's queue keeps a few chunks open and sends the oldest to the world
 * when there are too many: copies jumping from chunk to chunk would get a chunk sent while it is still written to,
 * and two versions of the same chunk applied at once lose blocks. So the copies are kept by chunk, and written
 * chunk after chunk once the queue has placed the blocks of the edit.
 * <p>
 * When the edit goes beyond the player's range (but not the configured maximum), it waits for FAWE's //confirm
 * to copy up to the maximum: before placing anything for a region, at the first block too far otherwise.
 */
final class SymmetryExtent extends AbstractDelegateExtent {

    // Copies kept before writing them (about 20 MB)
    private static final int MAX_PENDING = 250_000;

    /**
     * The player's answer to going beyond their range.
     */
    private enum Answer {
        NOT_ASKED, CONFIRMED, REFUSED
    }

    private Symmetry symmetry;
    private final int maxRange;
    private final int confirmSeconds;
    private final SymmetrySessions sessions;
    private Answer answer = Answer.NOT_ASKED;
    private final int minY;
    private final int maxY;
    // Copies by chunk, then by position: the last block set at a position wins
    private final Map<Long, Map<BlockVector3, BlockStateHolder<?>>> pending = new HashMap<>();
    private int pendingCount;
    // Blocks set too far from the point to be copied
    private int outOfRange;
    private final Actor actor;

    SymmetryExtent(Extent extent, Symmetry symmetry, int maxRange, int confirmSeconds, Actor actor,
                   SymmetrySessions sessions) {
        super(extent);
        this.sessions = sessions;
        this.actor = actor;
        this.symmetry = symmetry;
        this.maxRange = maxRange;
        this.confirmSeconds = confirmSeconds;
        this.minY = extent.getMinY();
        this.maxY = extent.getMaxY();
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
        return setBlock(position.x(), position.y(), position.z(), block);
    }

    @Override
    public synchronized <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block)
            throws WorldEditException {
        boolean changed = getExtent().setBlock(x, y, z, block);
        if (!symmetry.inRange(x, y, z, maxRange)) {
            if (canExtend() && symmetry.withRange(0).inRange(x, y, z, maxRange)) {
                ask();
            }
            if (!symmetry.inRange(x, y, z, maxRange)) {
                outOfRange++;
                return changed;
            }
        }
        for (Symmetry.Copy copy : symmetry.copies(x, y, z, maxRange)) {
            BlockVector3 target = copy.position();
            if (target.y() >= minY && target.y() <= maxY) {
                long chunk = ((long) (target.x() >> 4) << 32) | ((target.z() >> 4) & 0xffffffffL);
                if (pending.computeIfAbsent(chunk, k -> new HashMap<>())
                        .put(target, WorldEditMirror.transform(block, copy.transform())) == null) {
                    pendingCount++;
                }
            }
        }
        if (pendingCount >= MAX_PENDING) {
            writeCopies();
        }
        return changed;
    }

    /**
     * Writes the copies kept, between two flushes of the queue so that no chunk is in two versions at once.
     */
    private void writeCopies() throws WorldEditException {
        if (pending.isEmpty()) {
            return;
        }
        IQueueExtent<?> queue = new ExtentTraverser<>(getExtent()).findAndGet(IQueueExtent.class);
        if (queue != null) {
            queue.flush();
        }
        for (Map<BlockVector3, BlockStateHolder<?>> chunk : pending.values()) {
            for (Map.Entry<BlockVector3, BlockStateHolder<?>> copy : chunk.entrySet()) {
                place(getExtent(), copy.getKey(), copy.getValue());
            }
        }
        pending.clear();
        pendingCount = 0;
        if (queue != null) {
            queue.flush();
        }
    }

    // Called when the edit ends, before the queue below is flushed
    @Override
    protected synchronized Operation commitBefore() {
        try {
            writeCopies();
        } catch (WorldEditException e) {
            throw new IllegalStateException("Could not write the symmetric copies", e);
        }
        if (outOfRange > 0) {
            Messages.send(actor, "symmetry.out-of-range", "count", outOfRange, "range", symmetry.range(maxRange));
            outOfRange = 0;
        }
        return null;
    }

    /**
     * @return true if the player was not asked yet and their range is under the configured maximum
     */
    private boolean canExtend() {
        return answer == Answer.NOT_ASKED && symmetry.range(maxRange) < maxRange;
    }

    /**
     * Asks before an edit on a region that goes beyond the player's range.
     */
    private synchronized void checkRegion(Region region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        if (canExtend() && (!symmetry.inRange(min.x(), min.y(), min.z(), maxRange)
                || !symmetry.inRange(max.x(), max.y(), max.z(), maxRange))) {
            ask();
        }
    }

    /**
     * Waits for FAWE's //confirm (or //cancel) like FAWE's own confirmations. The server thread cannot wait:
     * there the copies stay within the range.
     */
    private void ask() {
        answer = Answer.REFUSED;
        int range = symmetry.range(maxRange);
        Player player = actor.isPlayer() ? Bukkit.getPlayer(actor.getUniqueId()) : null;
        if (player == null || Fawe.isMainThread()) {
            return;
        }
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        InterruptableCondition wait = new InterruptableCondition(lock, condition, Thread.currentThread());
        lock.lock();
        try {
            actor.setMeta("cmdConfirm", wait);
            Component question = Messages.get("symmetry.confirm", "range", range, "max", maxRange,
                    "seconds", confirmSeconds);
            player.sendMessage((question == null ? Component.empty() : question.append(Component.space()))
                    .append(button("symmetry.confirm-button", "//confirm"))
                    .append(Component.space())
                    .append(button("symmetry.cancel-button", "//cancel")));
            if (condition.await(confirmSeconds, TimeUnit.SECONDS)) {
                answer = Answer.CONFIRMED;
                symmetry = symmetry.withRange(0);
                // The player's range becomes the maximum, for the next edits too
                sessions.update(actor.getUniqueId(), current -> current.withRange(0));
                Messages.send(actor, "symmetry.confirmed", "max", maxRange);
            } else {
                Messages.send(actor, "symmetry.not-confirmed", "range", range);
            }
        } catch (InterruptedException e) {
            // //cancel (which also stops the edit) or another command
            Messages.send(actor, "symmetry.not-confirmed", "range", range);
        } finally {
            if (actor.getMeta("cmdConfirm") == wait) {
                actor.deleteMeta("cmdConfirm");
            }
            lock.unlock();
        }
    }

    private static Component button(String key, String command) {
        return Messages.plain(key)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void place(Extent extent, BlockVector3 target, BlockStateHolder block) throws WorldEditException {
        extent.setBlock(target.x(), target.y(), target.z(), block);
    }

    @Override
    public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block) throws MaxChangedBlocksException {
        checkRegion(region);
        int count = 0;
        for (BlockVector3 position : region) {
            if (setBlock(position, block)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkRegion(region);
        return applyPattern(region, pattern);
    }

    @Override
    public int setBlocks(Set<BlockVector3> positions, Pattern pattern) {
        if (positions instanceof Region region) {
            checkRegion(region);
        }
        return applyPattern(positions, pattern);
    }

    private int applyPattern(Iterable<BlockVector3> positions, Pattern pattern) {
        int count = 0;
        for (BlockVector3 position : positions) {
            if (pattern.apply(this, position, position)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public <B extends BlockStateHolder<B>> int replaceBlocks(Region region, Set<BaseBlock> filter, B replacement)
            throws MaxChangedBlocksException {
        return replaceBlocks(region, filter, (Pattern) replacement);
    }

    @Override
    public int replaceBlocks(Region region, Set<BaseBlock> filter, Pattern pattern) throws MaxChangedBlocksException {
        Mask mask = filter == null ? new ExistingBlockMask(this) : new BlockMask(this, filter);
        return replaceBlocks(region, mask, pattern);
    }

    @Override
    public int replaceBlocks(Region region, Mask mask, Pattern pattern) throws MaxChangedBlocksException {
        checkRegion(region);
        RegionVisitor visitor = new RegionVisitor(region, new RegionMaskingFilter(mask, new BlockReplace(this, pattern)), this);
        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }

    @Override
    public <T extends Filter> T apply(Region region, T filter, boolean full) {
        checkRegion(region);
        ExtentFilterBlock block = new ExtentFilterBlock(this);
        for (BlockVector3 position : region) {
            filter.applyBlock(block.init(position));
        }
        return filter;
    }
}

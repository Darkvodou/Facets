package fr.darkvodou.moretools.symmetry;

import com.fastasyncworldedit.core.extent.filter.block.ExtentFilterBlock;
import com.fastasyncworldedit.core.queue.Filter;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.fastasyncworldedit.core.util.ExtentTraverser;
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
import fr.darkvodou.moretools.Messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
 */
final class SymmetryExtent extends AbstractDelegateExtent {

    // Copies kept before writing them (about 20 MB)
    private static final int MAX_PENDING = 250_000;

    private final Symmetry symmetry;
    private final int maxRange;
    private final int minY;
    private final int maxY;
    // Copies by chunk, then by position: the last block set at a position wins
    private final Map<Long, Map<BlockVector3, BlockStateHolder<?>>> pending = new HashMap<>();
    private int pendingCount;
    // Blocks set too far from the point to be copied
    private int outOfRange;
    private final Actor actor;

    SymmetryExtent(Extent extent, Symmetry symmetry, int maxRange, Actor actor) {
        super(extent);
        this.actor = actor;
        this.symmetry = symmetry;
        this.maxRange = maxRange;
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
            outOfRange++;
            return changed;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void place(Extent extent, BlockVector3 target, BlockStateHolder block) throws WorldEditException {
        extent.setBlock(target.x(), target.y(), target.z(), block);
    }

    @Override
    public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block) throws MaxChangedBlocksException {
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
        return applyPattern(region, pattern);
    }

    @Override
    public int setBlocks(Set<BlockVector3> positions, Pattern pattern) {
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
        RegionVisitor visitor = new RegionVisitor(region, new RegionMaskingFilter(mask, new BlockReplace(this, pattern)), this);
        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }

    @Override
    public <T extends Filter> T apply(Region region, T filter, boolean full) {
        ExtentFilterBlock block = new ExtentFilterBlock(this);
        for (BlockVector3 position : region) {
            filter.applyBlock(block.init(position));
        }
        return filter;
    }
}

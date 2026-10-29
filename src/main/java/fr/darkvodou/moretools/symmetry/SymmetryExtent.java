package fr.darkvodou.moretools.symmetry;

import com.fastasyncworldedit.core.extent.filter.block.ExtentFilterBlock;
import com.fastasyncworldedit.core.queue.Filter;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import java.util.Set;

/**
 * Copies every block an edit sets to its symmetric positions. Sits above FAWE's history, so the copies are undone
 * with the edit, and below nothing: the copies are not copied again.
 * <p>
 * FAWE's fast paths (whole regions at once) skip setBlock, so they are done here block by block.
 */
final class SymmetryExtent extends AbstractDelegateExtent {

    private final Symmetry symmetry;
    private final int maxRange;
    private final int minY;
    private final int maxY;

    SymmetryExtent(Extent extent, Symmetry symmetry, int maxRange) {
        super(extent);
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
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
        boolean changed = getExtent().setBlock(x, y, z, block);
        for (Symmetry.Copy copy : symmetry.copies(x, y, z, maxRange)) {
            BlockVector3 target = copy.position();
            if (target.y() >= minY && target.y() <= maxY) {
                place(getExtent(), target, WorldEditMirror.transform(block, copy.transform()));
            }
        }
        return changed;
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

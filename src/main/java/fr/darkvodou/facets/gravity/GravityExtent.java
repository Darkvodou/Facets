package fr.darkvodou.facets.gravity;

import com.fastasyncworldedit.core.extent.filter.block.ExtentFilterBlock;
import com.fastasyncworldedit.core.math.BlockVectorSet;
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
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.registry.BlockMaterial;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Makes the falling blocks set by an edit (sand, gravel, concrete powder, anvils...) fall, without falling block
 * entities: they are written where they land, so nothing drops. Sits above FAWE's history, so the falls are undone
 * with the edit.
 * <p>
 * The falling blocks are kept until the end of the edit, then written once, where they land: FAWE's history keeps
 * each write, and the undo of a position written twice can leave its first block. The other blocks are written
 * right away, and their positions remembered with whether a falling block goes through them.
 * <p>
 * A falling block goes through air, liquids and the blocks replaced when placing a block (grass, fire...), and
 * falls out of the world when nothing stops it. A concrete powder landing in water becomes concrete.
 * <p>
 * FAWE's fast paths (whole regions at once) skip setBlock, so they are done here block by block.
 */
final class GravityExtent extends AbstractDelegateExtent {

    private static final BaseBlock AIR = BlockTypes.AIR.getDefaultState().toBaseBlock();

    private final int minY;
    // The falling blocks of the edit, not written yet, by column
    private final Map<Long, Column> columns = new HashMap<>();
    // Positions where the edit wrote another block, the free ones among them, and the water among those
    private final BlockVectorSet written = new BlockVectorSet();
    private final BlockVectorSet free = new BlockVectorSet();
    private final BlockVectorSet water = new BlockVectorSet();

    GravityExtent(Extent extent) {
        super(extent);
        this.minY = extent.getMinY();
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
        return setBlock(position.x(), position.y(), position.z(), block);
    }

    @Override
    public synchronized <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block)
            throws WorldEditException {
        long key = key(x, z);
        Column column = columns.get(key);
        BlockType type = block.getBlockType();
        if (Types.FALLING.contains(type)) {
            if (column == null) {
                column = new Column();
                columns.put(key, column);
            }
            column.add(y, block.toBaseBlock());
            // The block written before is replaced at the end, whether this one falls or not
            free.remove(x, y, z);
            water.remove(x, y, z);
            return true;
        }
        if (column != null) {
            column.remove(y);
        }
        written.add(x, y, z);
        mark(free, x, y, z, isFree(type));
        mark(water, x, y, z, isWater(type));
        return getExtent().setBlock(x, y, z, block);
    }

    // Called when the edit ends, before the queue below is flushed
    @Override
    protected synchronized Operation commitBefore() {
        try {
            for (Map.Entry<Long, Column> entry : columns.entrySet()) {
                long key = entry.getKey();
                fall((int) (key >> 32), (int) key, entry.getValue());
            }
        } catch (WorldEditException e) {
            throw new IllegalStateException("Could not drop the falling blocks", e);
        }
        columns.clear();
        return null;
    }

    private void fall(int x, int z, Column column) throws WorldEditException {
        // The last block set at each height
        TreeMap<Integer, BaseBlock> blocks = new TreeMap<>();
        for (int i = 0; i < column.size; i++) {
            if (column.blocks[i] == null) {
                blocks.remove(column.ys[i]);
            } else {
                blocks.put(column.ys[i], column.blocks[i]);
            }
        }
        if (blocks.isEmpty()) {
            return;
        }
        int[] ys = blocks.keySet().stream().mapToInt(Integer::intValue).toArray();
        BaseBlock[] falling = blocks.values().toArray(BaseBlock[]::new);
        int[] landings = Falls.land(ys, minY, y -> written.contains(x, y, z)
                ? free.contains(x, y, z)
                : isFree(getExtent().getBlock(x, y, z).getBlockType()));
        Map<Integer, BaseBlock> writes = new HashMap<>();
        for (int i = 0; i < ys.length; i++) {
            int y = ys[i];
            // Where a block left, what was there before the edit stays if it was free (water, grass...)
            if (landings[i] != y && (written.contains(x, y, z)
                    || !isFree(getExtent().getBlock(x, y, z).getBlockType()))) {
                writes.put(y, AIR);
            }
        }
        for (int i = 0; i < ys.length; i++) {
            int y = landings[i];
            if (y >= minY) {
                writes.put(y, inWater(x, y, z) ? concrete(falling[i]) : falling[i]);
            }
        }
        for (Map.Entry<Integer, BaseBlock> write : writes.entrySet()) {
            getExtent().setBlock(x, write.getKey(), z, write.getValue());
        }
    }

    private boolean inWater(int x, int y, int z) {
        return written.contains(x, y, z) ? water.contains(x, y, z) : isWater(getExtent().getBlock(x, y, z).getBlockType());
    }

    private static BaseBlock concrete(BaseBlock block) {
        String id = Falls.concrete(block.getBlockType().id());
        BlockType concrete = id == null ? null : BlockTypes.get(id);
        return concrete == null ? block : concrete.getDefaultState().toBaseBlock();
    }

    private static boolean isFree(BlockType type) {
        BlockMaterial material = type.getMaterial();
        return material.isAir() || material.isLiquid() || material.isReplacedDuringPlacement();
    }

    private static boolean isWater(BlockType type) {
        return type == BlockTypes.WATER || type == BlockTypes.BUBBLE_COLUMN;
    }

    private static void mark(BlockVectorSet set, int x, int y, int z, boolean value) {
        if (value) {
            set.add(x, y, z);
        } else {
            set.remove(x, y, z);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
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

    /**
     * The falling blocks set in a column, in order. A removed block (written over by another block) is null.
     */
    private static final class Column {

        private int[] ys = new int[4];
        private BaseBlock[] blocks = new BaseBlock[4];
        private int size;

        void add(int y, BaseBlock block) {
            if (size == ys.length) {
                ys = Arrays.copyOf(ys, size * 2);
                blocks = Arrays.copyOf(blocks, size * 2);
            }
            ys[size] = y;
            blocks[size++] = block;
        }

        void remove(int y) {
            for (int i = 0; i < size; i++) {
                if (ys[i] == y) {
                    blocks[i] = null;
                }
            }
        }
    }

    /**
     * The falling block types, read once the block registry is loaded.
     */
    private static final class Types {

        static final Set<BlockType> FALLING = BlockType.REGISTRY.values().stream()
                .filter(type -> Falls.falls(type.id()))
                .collect(Collectors.toUnmodifiableSet());
    }
}

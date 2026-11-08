package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's symmetry: its point, its type and how far from the point blocks are copied, and maybe a second
 * symmetry that copies the blocks and the copies of the first.
 *
 * @param world   name of the world of the points
 * @param center  the block of the symmetry point: it is its own copy, unless the point is even
 * @param even    the point is between blocks, at the corner of {@code center} toward +x, +y and +z: mirrored blocks
 *                go in pairs (blocks x and x + 1 are copies of each other for planx)
 * @param range   distance from each point (along each axis) up to which blocks are copied, 0 for the configured maximum
 * @param enabled false while paused with //sym toggle
 * @param second  the second symmetry, applied after the first, or null
 */
public record Symmetry(String world, BlockVector3 center, boolean even, SymmetryType type, int range,
                       boolean enabled, @Nullable Second second) {

    /**
     * The most blocks placed for one: a megastar and a star3d together, never two megastars.
     */
    public static final int MAX_COPIES = SymmetryType.MEGASTAR.copies() * SymmetryType.STAR3D.copies();

    /**
     * A copy of a block, with the transform that turns its state.
     */
    public record Copy(BlockVector3 position, Transform transform) {
    }

    /**
     * The second symmetry: its point and its type, in the world and with the range of the first.
     *
     * @param even    the point is between blocks, like that of the first symmetry
     * @param enabled false while the second symmetry alone is paused with //sym2 toggle
     */
    public record Second(BlockVector3 center, boolean even, SymmetryType type, boolean enabled) {

        public Second(BlockVector3 center, SymmetryType type) {
            this(center, false, type);
        }

        public Second(BlockVector3 center, boolean even, SymmetryType type) {
            this(center, even, type, true);
        }

        public Second withType(SymmetryType type) {
            return new Second(center, even, type, enabled);
        }

        public Second withEnabled(boolean enabled) {
            return new Second(center, even, type, enabled);
        }
    }

    public Symmetry(String world, BlockVector3 center, SymmetryType type, int range, boolean enabled) {
        this(world, center, false, type, range, enabled, null);
    }

    public Symmetry withType(SymmetryType type) {
        return new Symmetry(world, center, even, type, range, enabled, second);
    }

    public Symmetry withRange(int range) {
        return new Symmetry(world, center, even, type, range, enabled, second);
    }

    public Symmetry withEnabled(boolean enabled) {
        return new Symmetry(world, center, even, type, range, enabled, second);
    }

    public Symmetry withSecond(@Nullable Second second) {
        return new Symmetry(world, center, even, type, range, enabled, second);
    }

    /**
     * @return the first symmetry, as a second one
     */
    public Second first() {
        return new Second(center, even, type);
    }

    /**
     * @return this symmetry with the first one replaced, keeping the world, the range, the pause and the second one
     */
    public Symmetry withFirst(Second first) {
        return new Symmetry(world, first.center(), first.even(), first.type(), range, enabled, second);
    }

    /**
     * The first symmetry becomes the second and the second the first, which changes the copies when they are
     * different: the second also copies the copies of the first. The pause of the second stays with the second.
     *
     * @return the swapped symmetries, or this one if there is no second
     */
    public Symmetry swapped() {
        return second == null ? this : withFirst(second).withSecond(first().withEnabled(second.enabled()));
    }

    /**
     * @return the second symmetry if it is not paused on its own, else null
     */
    @Nullable
    public Second activeSecond() {
        return second != null && second.enabled() ? second : null;
    }

    /**
     * @return whether the two types can be combined, under {@link #MAX_COPIES}
     */
    public static boolean combinable(SymmetryType first, @Nullable SymmetryType second) {
        return second == null || first.copies() * second.copies() <= MAX_COPIES;
    }

    /**
     * @return the number of blocks placed for one at most, counting it
     */
    public int copyCount() {
        Second second = activeSecond();
        return type.copies() * (second == null ? 1 : second.type().copies());
    }

    /**
     * @return the range used, never over the configured maximum
     */
    public int range(int maxRange) {
        return range <= 0 ? maxRange : Math.min(range, maxRange);
    }

    /**
     * @return whether the block is within the range of one of the points
     */
    public boolean inRange(int x, int y, int z, int maxRange) {
        int range = range(maxRange);
        Second second = activeSecond();
        return inRange(center, even, x, y, z, range)
                || (second != null && inRange(second.center(), second.even(), x, y, z, range));
    }

    /**
     * Even points have {@code range} blocks on each side, odd ones the same around their block.
     */
    private static boolean inRange(BlockVector3 center, boolean even, int x, int y, int z, int range) {
        // In half blocks, from the point
        int half = even ? 1 : 0;
        int limit = 2 * range;
        return Math.abs(2 * (x - center.x()) - half) <= limit && Math.abs(2 * (y - center.y()) - half) <= limit
                && Math.abs(2 * (z - center.z()) - half) <= limit;
    }

    /**
     * The first symmetry copies the block, then the second copies the block and those copies: each symmetry only
     * copies what is within the range of its point.
     *
     * @return the copies of the block, without the block itself and without the copies landing on it,
     * or nothing if it is out of range
     */
    public List<Copy> copies(int x, int y, int z, int maxRange) {
        if (!inRange(x, y, z, maxRange)) {
            return List.of();
        }
        int range = range(maxRange);
        BlockVector3 origin = BlockVector3.at(x, y, z);
        // The first copy landing on a position wins
        Map<BlockVector3, Transform> copies = new LinkedHashMap<>();
        copies.put(origin, Transform.IDENTITY);
        if (inRange(center, even, x, y, z, range)) {
            add(copies, origin, Transform.IDENTITY, center, even, type);
        }
        Second second = activeSecond();
        if (second != null) {
            for (Map.Entry<BlockVector3, Transform> copy : new ArrayList<>(copies.entrySet())) {
                BlockVector3 position = copy.getKey();
                if (inRange(second.center(), second.even(), position.x(), position.y(), position.z(), range)) {
                    add(copies, position, copy.getValue(), second.center(), second.even(), second.type());
                }
            }
        }
        copies.remove(origin);
        List<Copy> result = new ArrayList<>(copies.size());
        copies.forEach((position, transform) -> result.add(new Copy(position, transform)));
        return result;
    }

    /**
     * Works in half blocks from the point, so that even points (between blocks) give whole blocks too: every axis
     * has the same parity, which the transforms keep.
     */
    private static void add(Map<BlockVector3, Transform> copies, BlockVector3 position, Transform before,
                            BlockVector3 center, boolean even, SymmetryType type) {
        int half = even ? 1 : 0;
        int dx = 2 * (position.x() - center.x()) - half;
        int dy = 2 * (position.y() - center.y()) - half;
        int dz = 2 * (position.z() - center.z()) - half;
        for (Transform transform : type.transforms()) {
            int[] offset = transform.apply(dx, dy, dz);
            copies.putIfAbsent(BlockVector3.at(center.x() + (offset[0] + half) / 2, center.y() + (offset[1] + half) / 2,
                    center.z() + (offset[2] + half) / 2), transform.after(before));
        }
    }
}

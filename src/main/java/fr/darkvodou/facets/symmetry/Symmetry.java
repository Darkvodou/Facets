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
 * @param center  the block of the symmetry point: it is its own copy
 * @param range   distance from each point (along each axis) up to which blocks are copied, 0 for the configured maximum
 * @param enabled false while paused with //sym toggle
 * @param second  the second symmetry, applied after the first, or null
 */
public record Symmetry(String world, BlockVector3 center, SymmetryType type, int range, boolean enabled,
                       @Nullable Second second) {

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
     */
    public record Second(BlockVector3 center, SymmetryType type) {
    }

    public Symmetry(String world, BlockVector3 center, SymmetryType type, int range, boolean enabled) {
        this(world, center, type, range, enabled, null);
    }

    public Symmetry withType(SymmetryType type) {
        return new Symmetry(world, center, type, range, enabled, second);
    }

    public Symmetry withRange(int range) {
        return new Symmetry(world, center, type, range, enabled, second);
    }

    public Symmetry withEnabled(boolean enabled) {
        return new Symmetry(world, center, type, range, enabled, second);
    }

    public Symmetry withSecond(@Nullable Second second) {
        return new Symmetry(world, center, type, range, enabled, second);
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
        return inRange(center, x, y, z, range) || (second != null && inRange(second.center(), x, y, z, range));
    }

    private static boolean inRange(BlockVector3 center, int x, int y, int z, int range) {
        return Math.abs(x - center.x()) <= range && Math.abs(y - center.y()) <= range && Math.abs(z - center.z()) <= range;
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
        if (inRange(center, x, y, z, range)) {
            add(copies, origin, Transform.IDENTITY, center, type);
        }
        if (second != null) {
            for (Map.Entry<BlockVector3, Transform> copy : new ArrayList<>(copies.entrySet())) {
                BlockVector3 position = copy.getKey();
                if (inRange(second.center(), position.x(), position.y(), position.z(), range)) {
                    add(copies, position, copy.getValue(), second.center(), second.type());
                }
            }
        }
        copies.remove(origin);
        List<Copy> result = new ArrayList<>(copies.size());
        copies.forEach((position, transform) -> result.add(new Copy(position, transform)));
        return result;
    }

    private static void add(Map<BlockVector3, Transform> copies, BlockVector3 position, Transform before,
                            BlockVector3 center, SymmetryType type) {
        for (Transform transform : type.transforms()) {
            int[] offset = transform.apply(position.x() - center.x(), position.y() - center.y(), position.z() - center.z());
            copies.putIfAbsent(BlockVector3.at(center.x() + offset[0], center.y() + offset[1], center.z() + offset[2]),
                    transform.after(before));
        }
    }
}

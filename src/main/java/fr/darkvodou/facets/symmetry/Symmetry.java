package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's symmetry: its point, its type and how far from the point blocks are copied.
 *
 * @param world   name of the world of the point
 * @param center  the block of the symmetry point: it is its own copy
 * @param range   distance from the point (along each axis) up to which blocks are copied, 0 for the configured maximum
 * @param enabled false while paused with //sym toggle
 */
public record Symmetry(String world, BlockVector3 center, SymmetryType type, int range, boolean enabled) {

    /**
     * A copy of a block, with the transform that turns its state.
     */
    public record Copy(BlockVector3 position, Transform transform) {
    }

    public Symmetry withType(SymmetryType type) {
        return new Symmetry(world, center, type, range, enabled);
    }

    public Symmetry withRange(int range) {
        return new Symmetry(world, center, type, range, enabled);
    }

    public Symmetry withEnabled(boolean enabled) {
        return new Symmetry(world, center, type, range, enabled);
    }

    /**
     * @return the range used, never over the configured maximum
     */
    public int range(int maxRange) {
        return range <= 0 ? maxRange : Math.min(range, maxRange);
    }

    public boolean inRange(int x, int y, int z, int maxRange) {
        int range = range(maxRange);
        return Math.abs(x - center.x()) <= range && Math.abs(y - center.y()) <= range && Math.abs(z - center.z()) <= range;
    }

    /**
     * @return the copies of the block, without the block itself and without the copies landing on it,
     * or nothing if it is out of range
     */
    public List<Copy> copies(int x, int y, int z, int maxRange) {
        if (!inRange(x, y, z, maxRange)) {
            return List.of();
        }
        List<Transform> transforms = type.transforms();
        List<Copy> copies = new ArrayList<>(transforms.size());
        for (Transform transform : transforms) {
            int[] offset = transform.apply(x - center.x(), y - center.y(), z - center.z());
            BlockVector3 position = BlockVector3.at(center.x() + offset[0], center.y() + offset[1], center.z() + offset[2]);
            if (position.x() != x || position.y() != y || position.z() != z) {
                copies.add(new Copy(position, transform));
            }
        }
        return copies;
    }
}

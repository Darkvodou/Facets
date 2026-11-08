package fr.darkvodou.facets.deform;

/**
 * The deformation of //fdeform magnet: the blocks of a zone are pulled toward a target, the closest ones the most
 * and the farthest ones not at all. The pull does not fade near the edges of the zone.
 * <p>
 * The full pull is on the first solid block (the closest to the target), not on the air around the target: with a
 * force of 100, that block goes 90% of the way to the target however far it is, and the pull fades from it to the
 * farthest block of the zone.
 * <p>
 * Each block of the zone takes the block found further away from the target on the same line (backward mapping):
 * every block gets one, so the deformation never leaves holes. Since the pull only gets weaker away from the target
 * and never reaches it, the blocks stay in the same order along each line (no folds). The blocks just past the
 * edges of the zone are pulled in, so the pull does not stop at the edges.
 * <p>
 * Positions are block coordinates: the target is in the same space, block (x, y, z) being at its center.
 */
final class Magnet {

    // Part of the way to the target that the closest blocks go with a force of 100
    private static final double MAX_PULL = 0.9;
    private static final int ITERATIONS = 18;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    // Distance to the target of the first solid block, and from it to the farthest block of the zone
    private final double nearest;
    private final double range;

    /**
     * @param inside for each block of the bounding box (x fastest, then z, then y), whether it is in the zone
     * @param solid  for each block of the bounding box, whether it is solid (pulled visibly, unlike air); with none,
     *               the full pull is on the closest block of the zone
     */
    Magnet(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, boolean[] inside, boolean[] solid,
            double targetX, double targetY, double targetZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        double nearest = Double.MAX_VALUE;
        double nearestSolid = Double.MAX_VALUE;
        double farthest = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = index(x, y, z);
                    if (inside[index]) {
                        double distance = distanceToTarget(minX + x, minY + y, minZ + z);
                        nearest = Math.min(nearest, distance);
                        farthest = Math.max(farthest, distance);
                        if (solid[index]) {
                            nearestSolid = Math.min(nearestSolid, distance);
                        }
                    }
                }
            }
        }
        if (nearestSolid != Double.MAX_VALUE) {
            nearest = nearestSolid;
        }
        this.nearest = nearest == Double.MAX_VALUE ? 0 : nearest;
        this.range = Math.max(1, farthest - this.nearest);
    }

    /**
     * @param force from 0 (nothing moves) to 100 (the first solid block goes 90% of the way to the target)
     * @return the block whose content goes to (x, y, z): itself when it does not move
     */
    long source(int x, int y, int z, double force) {
        double scale = MAX_PULL * Math.max(0, Math.min(100, force)) / 100;
        double ux = x - targetX;
        double uy = y - targetY;
        double uz = z - targetZ;
        if (scale <= 0 || ux * ux + uy * uy + uz * uz < 1e-9 || scale * weight(x, y, z) < 1e-4) {
            return pack(x, y, z);
        }
        // The block at target + u * l goes to target + u * l * (1 - pull): the l going to 1 is searched on the line,
        // between this block (l = 1, where it is still too short) and the farthest distance (where nothing moves)
        double low = 1;
        double high = Math.max(1, (nearest + range) / Math.sqrt(ux * ux + uy * uy + uz * uz));
        for (int i = 0; i < ITERATIONS; i++) {
            double l = (low + high) / 2;
            double pull = scale * weight(targetX + ux * l, targetY + uy * l, targetZ + uz * l);
            if (l * (1 - pull) < 1) {
                low = l;
            } else {
                high = l;
            }
        }
        double l = (low + high) / 2;
        return pack((int) Math.floor(targetX + ux * l + 0.5), (int) Math.floor(targetY + uy * l + 0.5),
                (int) Math.floor(targetZ + uz * l + 0.5));
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
    }

    static int x(long packed) {
        return (int) (packed >> 38);
    }

    static int y(long packed) {
        return (int) (packed << 52 >> 52);
    }

    static int z(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /**
     * The pull at a point, from 0 to 1: 1 up to the distance of the first solid block, 0 at the distance of the
     * farthest block of the zone. It goes on past the edges of the zone, so that the blocks there are pulled in smoothly.
     */
    private double weight(double x, double y, double z) {
        double far = Math.max(0, Math.min(1, (distanceToTarget(x, y, z) - nearest) / range));
        return (1 - far) * (1 - far);
    }

    private double distanceToTarget(double x, double y, double z) {
        double dx = x - targetX;
        double dy = y - targetY;
        double dz = z - targetZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

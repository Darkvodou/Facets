package fr.darkvodou.moretools.fill;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Flood fill of the air around a point that does not go through small holes.
 * <p>
 * The fill is made of cubes of side tolerance + 1 that move through the air without touching a block:
 * they cannot pass a hole whose smallest side is at most the tolerance. The air next to these cubes is
 * added too, which plugs the holes and fills the corners the cubes do not reach.
 * A flat fill (a single layer of blocks) uses squares instead of cubes.
 */
public final class FuzzyFill {

    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int radius;
    private final int n;
    private final int side;
    // Height of the cubes: 1 for a flat fill
    private final int sideY;
    private final BlockVector3 origin;

    private final boolean[] air;
    // Cells of the reachable air, in order of distance to the start
    private final int[] order;
    private int orderSize;
    // Sums over the box [0, x) x [0, y) x [0, z), of size (n + 1)^3
    private final int[] sums;

    private FuzzyFill(BlockVector3 start, int radius, int tolerance, boolean flat) {
        this.radius = radius;
        this.n = 2 * radius + 1;
        this.side = tolerance + 1;
        this.sideY = flat ? 1 : side;
        this.origin = start.subtract(radius, radius, radius);
        this.air = new boolean[n * n * n];
        this.order = new int[n * n * n];
        this.sums = new int[(n + 1) * (n + 1) * (n + 1)];
    }

    /**
     * @param start     where the fill starts
     * @param radius    the fill stays in the sphere of this radius around the start
     * @param tolerance the largest hole (smallest side, in blocks) the fill does not go through; 0 fills everything
     * @param flat      whether the air is a single layer of blocks (squares are moved instead of cubes)
     * @param isAir     whether a block can be filled
     * @return the blocks to fill, empty if the start is not air or no space around it is wider than the tolerance
     */
    public static List<BlockVector3> blocks(BlockVector3 start, int radius, int tolerance, boolean flat,
                                            Predicate<BlockVector3> isAir) {
        if (radius < 0 || tolerance < 0 || tolerance > 2 * radius) {
            throw new IllegalArgumentException("radius " + radius + ", tolerance " + tolerance);
        }
        return new FuzzyFill(start, radius, tolerance, flat).compute(isAir);
    }

    private List<BlockVector3> compute(Predicate<BlockVector3> isAir) {
        findAir(isAir);
        if (orderSize == 0) {
            return List.of();
        }
        sum(air, true);
        int startCube = startCube();
        if (startCube < 0) {
            return List.of();
        }
        boolean[] cubes = moveCubes(startCube);
        sum(cubes, false);

        boolean[] covered = new boolean[air.length];
        for (int k = 0; k < orderSize; k++) {
            int cell = order[k];
            int x = x(cell), y = y(cell), z = z(cell);
            covered[cell] = box(Math.max(0, x - side + 1), Math.max(0, y - sideY + 1), Math.max(0, z - side + 1),
                    x + 1, y + 1, z + 1) > 0;
        }

        List<BlockVector3> blocks = new ArrayList<>();
        for (int k = 0; k < orderSize; k++) {
            int cell = order[k];
            if (covered[cell] || touches(covered, cell)) {
                blocks.add(origin.add(x(cell), y(cell), z(cell)));
            }
        }
        return blocks;
    }

    /**
     * Flood fill of the air in the sphere, from its center: the air it does not reach counts as blocks.
     */
    private void findAir(Predicate<BlockVector3> isAir) {
        boolean[] seen = new boolean[air.length];
        int center = index(radius, radius, radius);
        seen[center] = true;
        if (!isAir.test(origin.add(radius, radius, radius))) {
            return;
        }
        air[center] = true;
        order[orderSize++] = center;
        long maxDistance = (long) radius * radius;
        for (int k = 0; k < orderSize; k++) {
            int cell = order[k];
            int x = x(cell), y = y(cell), z = z(cell);
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!inside(nx, ny, nz)) {
                    continue;
                }
                int next = index(nx, ny, nz);
                if (seen[next]) {
                    continue;
                }
                seen[next] = true;
                long dx = nx - radius, dy = ny - radius, dz = nz - radius;
                if (dx * dx + dy * dy + dz * dz <= maxDistance && isAir.test(origin.add(nx, ny, nz))) {
                    air[next] = true;
                    order[orderSize++] = next;
                }
            }
        }
    }

    /**
     * The cube closest to the start that fits in the air and holds an air cell, or -1.
     */
    private int startCube() {
        for (int k = 0; k < orderSize; k++) {
            int cell = order[k];
            int x = x(cell), y = y(cell), z = z(cell);
            for (int cx = Math.max(0, x - side + 1); cx <= x; cx++) {
                for (int cy = Math.max(0, y - sideY + 1); cy <= y; cy++) {
                    for (int cz = Math.max(0, z - side + 1); cz <= z; cz++) {
                        if (fits(cx, cy, cz)) {
                            return index(cx, cy, cz);
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Moves the cube one block at a time from the start cube, while it fits.
     *
     * @return the lowest corners of the cubes reached
     */
    private boolean[] moveCubes(int startCube) {
        boolean[] reached = new boolean[air.length];
        int[] queue = new int[air.length];
        int size = 0;
        reached[startCube] = true;
        queue[size++] = startCube;
        for (int k = 0; k < size; k++) {
            int cube = queue[k];
            int x = x(cube), y = y(cube), z = z(cube);
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!fits(nx, ny, nz)) {
                    continue;
                }
                int next = index(nx, ny, nz);
                if (!reached[next]) {
                    reached[next] = true;
                    queue[size++] = next;
                }
            }
        }
        return reached;
    }

    private boolean fits(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x + side > n || y + sideY > n || z + side > n) {
            return false;
        }
        // The sums count the blocks
        return box(x, y, z, x + side, y + sideY, z + side) == 0;
    }

    private boolean touches(boolean[] covered, int cell) {
        int x = x(cell), y = y(cell), z = z(cell);
        for (int[] d : NEIGHBORS) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (inside(nx, ny, nz) && covered[index(nx, ny, nz)]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills {@link #sums} with the count of the cells whose value differs from {@code ignored}.
     */
    private void sum(boolean[] values, boolean ignored) {
        int m = n + 1;
        for (int x = 1; x <= n; x++) {
            for (int y = 1; y <= n; y++) {
                for (int z = 1; z <= n; z++) {
                    int value = values[index(x - 1, y - 1, z - 1)] != ignored ? 1 : 0;
                    sums[(x * m + y) * m + z] = value
                            + sums[((x - 1) * m + y) * m + z] + sums[(x * m + y - 1) * m + z] + sums[(x * m + y) * m + z - 1]
                            - sums[((x - 1) * m + y - 1) * m + z] - sums[((x - 1) * m + y) * m + z - 1]
                            - sums[(x * m + y - 1) * m + z - 1]
                            + sums[((x - 1) * m + y - 1) * m + z - 1];
                }
            }
        }
    }

    /**
     * Sum over the box [x0, x1) x [y0, y1) x [z0, z1).
     */
    private int box(int x0, int y0, int z0, int x1, int y1, int z1) {
        int m = n + 1;
        return sums[(x1 * m + y1) * m + z1]
                - sums[(x0 * m + y1) * m + z1] - sums[(x1 * m + y0) * m + z1] - sums[(x1 * m + y1) * m + z0]
                + sums[(x0 * m + y0) * m + z1] + sums[(x0 * m + y1) * m + z0] + sums[(x1 * m + y0) * m + z0]
                - sums[(x0 * m + y0) * m + z0];
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < n && y < n && z < n;
    }

    private int index(int x, int y, int z) {
        return (x * n + y) * n + z;
    }

    private int x(int index) {
        return index / (n * n);
    }

    private int y(int index) {
        return index / n % n;
    }

    private int z(int index) {
        return index % n;
    }
}

package fr.darkvodou.facets.fill;

import com.sk89q.worldedit.math.BlockVector3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Flood fill of the air around a point that does not go through small holes.
 * <p>
 * The fill is made of cubes of side tolerance + 1 that move through the air without touching a block:
 * they cannot pass a hole whose smallest side is at most the tolerance. The air next to these cubes is
 * added too, which plugs the holes and fills the corners the cubes do not reach.
 * A flat fill (a single layer of blocks, across any axis) uses squares instead of cubes.
 * <p>
 * The memory follows the air found, not the sphere: space is cut in sections of 16 blocks, made only where the fill
 * goes. Whether a cube fits, and which air the cubes cover, is found with runs of air along each axis in turn.
 */
public final class FuzzyFill {

    /**
     * Receives the blocks to fill.
     */
    public interface Output<E extends Exception> {
        void accept(BlockVector3 block) throws E;
    }

    /**
     * Thrown when the fill finds more air than allowed.
     */
    public static final class TooMuchAirException extends RuntimeException {

        public TooMuchAirException(long limit) {
            super("More than " + limit + " blocks of air");
        }
    }

    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    // Coordinates relative to the start are packed on 21 bits each
    private static final int BITS = 21;
    private static final int OFFSET = 1 << (BITS - 1);
    private static final long MASK = (1L << BITS) - 1;
    private static final int MAX_RADIUS = OFFSET - 16;

    // Layers of bits in a section
    private static final int SEEN = 0;
    private static final int AIR = 1;
    private static final int FITS = 2;
    private static final int REACHED = 3;
    private static final int COVERED = 4;

    private final BlockVector3 start;
    private final long maxDistance;
    // Sides of the cubes: 1 across a flat fill
    private final int[] side;
    private final long maxAir;
    private final Map<Long, Section> sections = new HashMap<>();
    // The section last looked up: most lookups land in the same one
    @Nullable
    private Section last;

    private FuzzyFill(BlockVector3 start, int radius, int tolerance, @Nullable BlockVector3 flat, long maxAir) {
        this.start = start;
        this.maxDistance = (long) radius * radius;
        int cube = tolerance + 1;
        this.side = new int[]{
                flat != null && flat.x() != 0 ? 1 : cube,
                flat != null && flat.y() != 0 ? 1 : cube,
                flat != null && flat.z() != 0 ? 1 : cube};
        this.maxAir = maxAir;
    }

    /**
     * @param start     where the fill starts
     * @param radius    the fill stays in the sphere of this radius around the start
     * @param tolerance the largest hole (smallest side, in blocks) the fill does not go through; 0 fills everything
     * @param flat      the direction across the air when it is a single layer of blocks (squares are moved
     *                  instead of cubes), or null
     * @param maxAir    the most air the fill may look through
     * @param isAir     whether a block can be filled, asked before any block is given to the output
     * @param output    receives the blocks to fill
     * @return the number of blocks given to the output: 0 if the start is not air or no space around it is wider than
     * the tolerance
     * @throws TooMuchAirException if the fill finds more than maxAir blocks of air
     */
    public static <E extends Exception> long fill(BlockVector3 start, int radius, int tolerance, @Nullable BlockVector3 flat,
                                                  long maxAir, Predicate<BlockVector3> isAir, Output<E> output) throws E {
        if (radius < 0 || radius > MAX_RADIUS || tolerance < 0 || tolerance > 2 * radius) {
            throw new IllegalArgumentException("radius " + radius + ", tolerance " + tolerance);
        }
        return new FuzzyFill(start, radius, tolerance, flat, maxAir).compute(isAir, output);
    }

    /**
     * {@link #fill} into a list, with no limit of air.
     */
    public static List<BlockVector3> blocks(BlockVector3 start, int radius, int tolerance, @Nullable BlockVector3 flat,
                                            Predicate<BlockVector3> isAir) {
        List<BlockVector3> blocks = new ArrayList<>();
        fill(start, radius, tolerance, flat, Long.MAX_VALUE, isAir, (Output<RuntimeException>) blocks::add);
        return blocks;
    }

    private <E extends Exception> long compute(Predicate<BlockVector3> isAir, Output<E> output) throws E {
        if (!findAir(isAir)) {
            return 0;
        }
        List<Section> withAir = new ArrayList<>();
        for (Section section : sections.values()) {
            if (section.any(AIR)) {
                section.run = new char[Section.SIZE];
                withAir.add(section);
            }
        }
        // A cube fits at its lowest corner when the air runs far enough along x, then these runs along y, then z
        for (int axis = 0; axis < 3; axis++) {
            fitRuns(withAir, axis);
        }
        for (Section section : withAir) {
            for (int i = 0; i < Section.SIZE; i++) {
                if (section.run[i] >= side[2]) {
                    section.mark(FITS, i);
                }
            }
        }
        long startCube = startCube();
        if (startCube < 0) {
            return 0;
        }
        moveCubes(startCube);
        // A block is covered by a cube when a reached corner is close enough behind it along x, then y, then z
        for (int axis = 0; axis < 3; axis++) {
            coverRuns(withAir, axis);
        }
        for (Section section : withAir) {
            for (int i = 0; i < Section.SIZE; i++) {
                if (section.has(AIR, i) && section.run[i] < side[2]) {
                    section.mark(COVERED, i);
                }
            }
            section.run = null;
        }

        long count = 0;
        for (Section section : withAir) {
            for (int i = 0; i < Section.SIZE; i++) {
                if (!section.has(AIR, i)) {
                    continue;
                }
                int x = section.x * 16 + (i & 15);
                int y = section.y * 16 + (i >> 8);
                int z = section.z * 16 + ((i >> 4) & 15);
                if (section.has(COVERED, i) || touchesCovered(x, y, z)) {
                    output.accept(start.add(x, y, z));
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Flood fill of the air in the sphere, from its center: the air it does not reach counts as blocks.
     *
     * @return false if the start is not air
     */
    private boolean findAir(Predicate<BlockVector3> isAir) {
        mark(SEEN, 0, 0, 0);
        if (!isAir.test(start)) {
            return false;
        }
        mark(AIR, 0, 0, 0);
        long count = 1;
        LongQueue queue = new LongQueue();
        queue.add(pack(0, 0, 0));
        while (!queue.isEmpty()) {
            long cell = queue.poll();
            int x = unpack(cell, 2), y = unpack(cell, 1), z = unpack(cell, 0);
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if ((long) nx * nx + (long) ny * ny + (long) nz * nz > maxDistance || has(SEEN, nx, ny, nz)) {
                    continue;
                }
                mark(SEEN, nx, ny, nz);
                if (isAir.test(start.add(nx, ny, nz))) {
                    if (++count > maxAir) {
                        throw new TooMuchAirException(maxAir);
                    }
                    mark(AIR, nx, ny, nz);
                    queue.add(pack(nx, ny, nz));
                }
            }
        }
        return true;
    }

    /**
     * Runs along the axis, from the far end: how far the previous runs reach their side, up to the side of this axis.
     * Along x the runs count air; along y they count the blocks whose x run reached its side, and so on.
     */
    private void fitRuns(List<Section> withAir, int axis) {
        withAir.sort(Comparator.comparingInt((Section s) -> s.coordinate(axis)).reversed());
        int limit = side[axis];
        int previous = axis == 0 ? 0 : side[axis - 1];
        int[] local = new int[3];
        for (Section section : withAir) {
            Section next = section(section.x + (axis == 0 ? 1 : 0), section.y + (axis == 1 ? 1 : 0),
                    section.z + (axis == 2 ? 1 : 0));
            for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    for (int w = 15; w >= 0; w--) {
                        int i = index(local, axis, u, v, w);
                        boolean own = axis == 0 ? section.has(AIR, i) : section.run[i] >= previous;
                        if (!own) {
                            section.run[i] = 0;
                            continue;
                        }
                        int after;
                        if (w < 15) {
                            after = section.run[index(local, axis, u, v, w + 1)];
                        } else {
                            after = next == null || next.run == null ? 0 : next.run[index(local, axis, u, v, 0)];
                        }
                        section.run[i] = (char) Math.min(limit, after + 1);
                    }
                }
            }
        }
    }

    /**
     * Runs along the axis, from the near end: how far behind is the closest block covered by the previous runs
     * (a reached corner along x), up to the side of this axis. Only air can be covered.
     */
    private void coverRuns(List<Section> withAir, int axis) {
        withAir.sort(Comparator.comparingInt((Section s) -> s.coordinate(axis)));
        int limit = side[axis];
        int previous = axis == 0 ? 0 : side[axis - 1];
        int[] local = new int[3];
        for (Section section : withAir) {
            Section before = section(section.x - (axis == 0 ? 1 : 0), section.y - (axis == 1 ? 1 : 0),
                    section.z - (axis == 2 ? 1 : 0));
            for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    for (int w = 0; w < 16; w++) {
                        int i = index(local, axis, u, v, w);
                        if (!section.has(AIR, i)) {
                            section.run[i] = (char) limit;
                            continue;
                        }
                        boolean own = axis == 0 ? section.has(REACHED, i) : section.run[i] < previous;
                        if (own) {
                            section.run[i] = 0;
                            continue;
                        }
                        int behind;
                        if (w > 0) {
                            behind = section.run[index(local, axis, u, v, w - 1)];
                        } else {
                            behind = before == null || before.run == null ? limit : before.run[index(local, axis, u, v, 15)];
                        }
                        section.run[i] = (char) Math.min(limit, behind + 1);
                    }
                }
            }
        }
    }

    /**
     * The cube closest to the start that fits and holds an air block, by its lowest corner, or -1.
     */
    private long startCube() {
        for (Section section : sections.values()) {
            section.clear(SEEN);
        }
        mark(SEEN, 0, 0, 0);
        LongQueue queue = new LongQueue();
        queue.add(pack(0, 0, 0));
        while (!queue.isEmpty()) {
            long cell = queue.poll();
            int x = unpack(cell, 2), y = unpack(cell, 1), z = unpack(cell, 0);
            for (int cx = x - side[0] + 1; cx <= x; cx++) {
                for (int cy = y - side[1] + 1; cy <= y; cy++) {
                    for (int cz = z - side[2] + 1; cz <= z; cz++) {
                        if (has(FITS, cx, cy, cz)) {
                            return pack(cx, cy, cz);
                        }
                    }
                }
            }
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (has(AIR, nx, ny, nz) && !has(SEEN, nx, ny, nz)) {
                    mark(SEEN, nx, ny, nz);
                    queue.add(pack(nx, ny, nz));
                }
            }
        }
        return -1;
    }

    /**
     * Moves the cube one block at a time from the start cube, while it fits.
     */
    private void moveCubes(long startCube) {
        LongQueue queue = new LongQueue();
        queue.add(startCube);
        mark(REACHED, unpack(startCube, 2), unpack(startCube, 1), unpack(startCube, 0));
        while (!queue.isEmpty()) {
            long cube = queue.poll();
            int x = unpack(cube, 2), y = unpack(cube, 1), z = unpack(cube, 0);
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (has(FITS, nx, ny, nz) && !has(REACHED, nx, ny, nz)) {
                    mark(REACHED, nx, ny, nz);
                    queue.add(pack(nx, ny, nz));
                }
            }
        }
    }

    private boolean touchesCovered(int x, int y, int z) {
        for (int[] d : NEIGHBORS) {
            if (has(COVERED, x + d[0], y + d[1], z + d[2])) {
                return true;
            }
        }
        return false;
    }

    private boolean has(int layer, int x, int y, int z) {
        Section section = section(x >> 4, y >> 4, z >> 4);
        return section != null && section.has(layer, Section.index(x, y, z));
    }

    private void mark(int layer, int x, int y, int z) {
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        Section section = section(sx, sy, sz);
        if (section == null) {
            section = new Section(sx, sy, sz);
            sections.put(pack(sx, sy, sz), section);
            last = section;
        }
        section.mark(layer, Section.index(x, y, z));
    }

    @Nullable
    private Section section(int sx, int sy, int sz) {
        Section cached = last;
        if (cached != null && cached.x == sx && cached.y == sy && cached.z == sz) {
            return cached;
        }
        Section section = sections.get(pack(sx, sy, sz));
        if (section != null) {
            last = section;
        }
        return section;
    }

    /**
     * The index in a section of the block at (u, v) across the axis and w along it.
     */
    private static int index(int[] local, int axis, int u, int v, int w) {
        local[axis] = w;
        local[(axis + 1) % 3] = u;
        local[(axis + 2) % 3] = v;
        return Section.index(local[0], local[1], local[2]);
    }

    private static long pack(int x, int y, int z) {
        return ((x + OFFSET) & MASK) << (2 * BITS) | ((y + OFFSET) & MASK) << BITS | ((z + OFFSET) & MASK);
    }

    // Part 2 is x, 1 is y, 0 is z
    private static int unpack(long packed, int part) {
        return (int) ((packed >>> (part * BITS)) & MASK) - OFFSET;
    }

    /**
     * 16 x 16 x 16 blocks, with a bit per block in each layer.
     */
    private static final class Section {

        static final int SIZE = 16 * 16 * 16;

        final int x;
        final int y;
        final int z;
        final long[][] layers = new long[COVERED + 1][SIZE / 64];
        // Lengths of runs while they are computed
        char[] run;

        Section(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static int index(int x, int y, int z) {
            return (x & 15) | (z & 15) << 4 | (y & 15) << 8;
        }

        int coordinate(int axis) {
            return axis == 0 ? x : axis == 1 ? y : z;
        }

        boolean has(int layer, int i) {
            return (layers[layer][i >> 6] >>> i & 1) != 0;
        }

        void mark(int layer, int i) {
            layers[layer][i >> 6] |= 1L << i;
        }

        boolean any(int layer) {
            for (long word : layers[layer]) {
                if (word != 0) {
                    return true;
                }
            }
            return false;
        }

        void clear(int layer) {
            java.util.Arrays.fill(layers[layer], 0);
        }
    }

    /**
     * A queue of longs that only grows to the most it holds at once.
     */
    private static final class LongQueue {

        private long[] items = new long[1024];
        private int head;
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void add(long value) {
            if (size == items.length) {
                long[] grown = new long[items.length * 2];
                for (int i = 0; i < size; i++) {
                    grown[i] = items[(head + i) & (items.length - 1)];
                }
                items = grown;
                head = 0;
            }
            items[(head + size) & (items.length - 1)] = value;
            size++;
        }

        long poll() {
            long value = items[head];
            head = (head + 1) & (items.length - 1);
            size--;
            return value;
        }
    }
}

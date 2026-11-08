package fr.darkvodou.facets.deform;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagnetTest {

    private static final int SIZE = 21;

    // A cube of 21 blocks from 0 to 20, the target 4 blocks above its center
    private static Magnet cube() {
        boolean[] inside = new boolean[SIZE * SIZE * SIZE];
        Arrays.fill(inside, true);
        return new Magnet(0, 0, 0, SIZE, SIZE, SIZE, inside, new boolean[inside.length], 10, 14, 10);
    }

    /**
     * A box from (0, 0, 0) of the given size, solid up to y = 3, the target at (10, 14, 10).
     */
    private static Magnet ground(int sizeX, int sizeY, int sizeZ) {
        boolean[] inside = new boolean[sizeX * sizeY * sizeZ];
        boolean[] solid = new boolean[inside.length];
        Arrays.fill(inside, true);
        for (int y = 0; y <= 3; y++) {
            Arrays.fill(solid, y * sizeZ * sizeX, (y + 1) * sizeZ * sizeX, true);
        }
        return new Magnet(0, 0, 0, sizeX, sizeY, sizeZ, inside, solid, 10, 14, 10);
    }

    @Test
    void theFirstSolidBlockIsPulledAllTheWayWhateverTheSize() {
        // The ground at y = 3 is 11 blocks under the target: at 100, it rises to 1.1 blocks under it (y = 12.9),
        // in a box just around the target as in a large one
        for (Magnet magnet : new Magnet[]{ground(21, 21, 21), ground(13, 16, 13), ground(64, 40, 64)}) {
            assertTrue(Magnet.y(magnet.source(10, 12, 10, 100)) <= 3);
            assertTrue(Magnet.y(magnet.source(10, 13, 10, 100)) > 3);
        }
    }

    @Test
    void halfTheForcePullsHalfAsFar() {
        // At 50, the ground rises 45% of the 11 blocks: to y = 7.95, rounded to 8
        Magnet magnet = ground(21, 21, 21);
        assertTrue(Magnet.y(magnet.source(10, 8, 10, 50)) <= 3);
        assertTrue(Magnet.y(magnet.source(10, 9, 10, 50)) > 3);
    }

    @Test
    void packsCoordinates() {
        long packed = Magnet.pack(-30_000_000, -64, 29_999_999);
        assertEquals(-30_000_000, Magnet.x(packed));
        assertEquals(-64, Magnet.y(packed));
        assertEquals(29_999_999, Magnet.z(packed));
    }

    @Test
    void theFarthestBlocksDoNotMove() {
        Magnet magnet = cube();
        assertEquals(Magnet.pack(0, 0, 0), magnet.source(0, 0, 0, 100));
        assertEquals(Magnet.pack(20, 0, 20), magnet.source(20, 0, 20, 100));
    }

    @Test
    void theEdgesMoveToo() {
        // The middle of the bottom face takes a block from under the zone
        long source = cube().source(10, 0, 10, 100);
        assertTrue(Magnet.y(source) < 0);
    }

    @Test
    void noForceMovesNothing() {
        assertEquals(Magnet.pack(10, 5, 10), cube().source(10, 5, 10, 0));
    }

    @Test
    void blocksComeFromFurtherAway() {
        // Under the target, each block takes one from further down
        long source = cube().source(10, 10, 10, 100);
        assertEquals(10, Magnet.x(source));
        assertTrue(Magnet.y(source) < 10);
        assertEquals(10, Magnet.z(source));
    }

    @Test
    void sourcesKeepTheirOrderSoNothingIsLost() {
        // Down the line under the target, the sources only go down and take every block in turn
        Magnet magnet = cube();
        int previous = Integer.MAX_VALUE;
        for (int y = 13; y >= 0; y--) {
            int source = Magnet.y(magnet.source(10, y, 10, 100));
            assertTrue(source <= previous, "source of " + y + " is " + source + " after " + previous);
            assertTrue(previous == Integer.MAX_VALUE || previous - source <= 3, "skipped blocks at " + y);
            previous = source;
        }
    }
}

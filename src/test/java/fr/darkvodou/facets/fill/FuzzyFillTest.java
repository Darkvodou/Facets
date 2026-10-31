package fr.darkvodou.facets.fill;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyFillTest {

    // A 7x5x7 cave (x and z from -3 to 3, y from 0 to 4) in stone, with an open world on the side x > 4
    private static Predicate<BlockVector3> cave(Set<BlockVector3> holes) {
        return position -> {
            if (holes.contains(position) || position.x() > 4) {
                return true;
            }
            return Math.abs(position.x()) <= 3 && position.y() >= 0 && position.y() <= 4 && Math.abs(position.z()) <= 3;
        };
    }

    private static Set<BlockVector3> fill(Predicate<BlockVector3> isAir, int radius, int tolerance) {
        return new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), radius, tolerance, null, isAir));
    }

    @Test
    void fillsAClosedCave() {
        assertEquals(7 * 5 * 7, fill(cave(Set.of()), 20, 1).size());
    }

    @Test
    void plugsAHoleOfOneBlock() {
        BlockVector3 hole = BlockVector3.at(4, 2, 0);
        Set<BlockVector3> filled = fill(cave(Set.of(hole)), 20, 1);
        assertEquals(7 * 5 * 7 + 1, filled.size());
        assertTrue(filled.contains(hole));
        assertFalse(filled.contains(BlockVector3.at(5, 2, 0)));
    }

    @Test
    void goesThroughTheHoleWithoutTolerance() {
        Set<BlockVector3> filled = fill(cave(Set.of(BlockVector3.at(4, 2, 0))), 20, 0);
        assertTrue(filled.contains(BlockVector3.at(10, 2, 0)));
    }

    @Test
    void goesThroughAHoleWiderThanTheTolerance() {
        Set<BlockVector3> holes = Set.of(BlockVector3.at(4, 2, 0), BlockVector3.at(4, 3, 0),
                BlockVector3.at(4, 2, 1), BlockVector3.at(4, 3, 1));
        assertTrue(fill(cave(holes), 20, 1).contains(BlockVector3.at(10, 2, 0)));
        assertFalse(fill(cave(holes), 20, 2).contains(BlockVector3.at(5, 2, 0)));
    }

    @Test
    void staysInTheRadius() {
        Set<BlockVector3> filled = fill(position -> true, 5, 1);
        for (BlockVector3 block : filled) {
            assertTrue(block.distanceSq(BlockVector3.at(0, 1, 0)) <= 25);
        }
        assertTrue(filled.contains(BlockVector3.at(5, 1, 0)));
    }

    @Test
    void fillsOneLayerWithSquares() {
        BlockVector3 hole = BlockVector3.at(4, 1, 0);
        Predicate<BlockVector3> cave = cave(Set.of(hole));
        Set<BlockVector3> filled = new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), 20, 1, BlockVector3.UNIT_Y,
                position -> position.y() == 1 && cave.test(position)));
        assertEquals(7 * 7 + 1, filled.size());
        assertTrue(filled.contains(hole));
        assertFalse(filled.contains(BlockVector3.at(5, 1, 0)));
    }

    @Test
    void fillsAVerticalLayerWithSquares() {
        BlockVector3 hole = BlockVector3.at(4, 2, 0);
        Predicate<BlockVector3> cave = cave(Set.of(hole));
        Set<BlockVector3> filled = new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), 20, 1, BlockVector3.UNIT_Z,
                position -> position.z() == 0 && cave.test(position)));
        assertEquals(7 * 5 + 1, filled.size());
        assertTrue(filled.contains(hole));
        assertFalse(filled.contains(BlockVector3.at(5, 2, 0)));
    }

    @Test
    void fillsBelowWithoutGoingOverAWall() {
        // The hole is above the feet: the fill below cannot use it
        Predicate<BlockVector3> cave = cave(Set.of(BlockVector3.at(4, 3, 0), BlockVector3.at(4, 3, 1),
                BlockVector3.at(4, 4, 0), BlockVector3.at(4, 4, 1)));
        Set<BlockVector3> filled = new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), 20, 1, null,
                position -> position.y() <= 1 && cave.test(position)));
        assertEquals(7 * 2 * 7, filled.size());
    }

    @Test
    void doesNothingInsideABlock() {
        assertEquals(List.of(), FuzzyFill.blocks(BlockVector3.at(0, 0, 0), 5, 1, null, position -> false));
    }

    @Test
    void fillsLikeTheFillOverTheWholeBox() {
        Random random = new Random(42);
        BlockVector3[] flats = {null, BlockVector3.UNIT_Y, BlockVector3.UNIT_X, BlockVector3.UNIT_Z};
        int filled = 0;
        for (int run = 0; run < 120; run++) {
            int radius = 3 + random.nextInt(18);
            int tolerance = random.nextInt(Math.min(radius, 6) + 1);
            BlockVector3 flat = flats[random.nextInt(flats.length)];
            BlockVector3 start = BlockVector3.at(random.nextInt(100) - 50, random.nextInt(100) - 50, random.nextInt(100) - 50);
            // Random rock, dense or not, with a small room at the start
            double rock = random.nextDouble() * 0.4;
            long seed = random.nextLong();
            Predicate<BlockVector3> isAir = position -> {
                if (flat != null && (position.subtract(start).dot(flat) != 0)) {
                    return false;
                }
                if (position.distanceSq(start) <= 9) {
                    return true;
                }
                long hash = (position.x() * 73856093L) ^ (position.y() * 19349663L) ^ (position.z() * 83492791L) ^ seed;
                return new Random(hash).nextDouble() >= rock;
            };
            Set<BlockVector3> expected = new HashSet<>(DenseFuzzyFill.blocks(start, radius, tolerance, flat, isAir));
            assertEquals(expected, new HashSet<>(FuzzyFill.blocks(start, radius, tolerance, flat, isAir)),
                    "radius " + radius + ", tolerance " + tolerance + ", flat " + flat);
            if (expected.size() > 20) {
                filled++;
            }
        }
        assertTrue(filled >= 60, filled + " runs filled more than 20 blocks");
    }
}

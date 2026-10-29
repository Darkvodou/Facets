package fr.darkvodou.facets.fill;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
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
        return new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), radius, tolerance, false, isAir));
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
        Set<BlockVector3> filled = new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), 20, 1, true,
                position -> position.y() == 1 && cave.test(position)));
        assertEquals(7 * 7 + 1, filled.size());
        assertTrue(filled.contains(hole));
        assertFalse(filled.contains(BlockVector3.at(5, 1, 0)));
    }

    @Test
    void fillsBelowWithoutGoingOverAWall() {
        // The hole is above the feet: the fill below cannot use it
        Predicate<BlockVector3> cave = cave(Set.of(BlockVector3.at(4, 3, 0), BlockVector3.at(4, 3, 1),
                BlockVector3.at(4, 4, 0), BlockVector3.at(4, 4, 1)));
        Set<BlockVector3> filled = new HashSet<>(FuzzyFill.blocks(BlockVector3.at(0, 1, 0), 20, 1, false,
                position -> position.y() <= 1 && cave.test(position)));
        assertEquals(7 * 2 * 7, filled.size());
    }

    @Test
    void doesNothingInsideABlock() {
        assertEquals(List.of(), FuzzyFill.blocks(BlockVector3.at(0, 0, 0), 5, 1, false, position -> false));
    }
}

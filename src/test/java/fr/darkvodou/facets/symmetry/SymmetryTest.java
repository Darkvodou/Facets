package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryTest {

    private static Map<BlockVector3, Transform> copies(Symmetry symmetry, int x, int y, int z, int maxRange) {
        Map<BlockVector3, Transform> copies = new HashMap<>();
        for (Symmetry.Copy copy : symmetry.copies(x, y, z, maxRange)) {
            copies.put(copy.position(), copy.transform());
        }
        return copies;
    }

    @Test
    void secondCopiesTheCopiesOfTheFirst() {
        // Mirror across x = 0, then across z = 10
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, SymmetryType.PLANX, 0, true)
                .withSecond(new Symmetry.Second(BlockVector3.at(0, 0, 10), SymmetryType.PLANZ));
        Map<BlockVector3, Transform> copies = copies(symmetry, 3, 5, 2, 100);
        assertEquals(Map.of(
                BlockVector3.at(-3, 5, 2), Transform.MIRROR_X,
                BlockVector3.at(3, 5, 18), Transform.MIRROR_Z,
                BlockVector3.at(-3, 5, 18), Transform.MIRROR_Z.after(Transform.MIRROR_X)), copies);
    }

    @Test
    void eachSymmetryCopiesWithinItsRange() {
        // The second point is 50 blocks away, range 10: the block is only near the first
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, SymmetryType.PLANX, 10, true)
                .withSecond(new Symmetry.Second(BlockVector3.at(50, 0, 0), SymmetryType.PLANX));
        assertEquals(Map.of(BlockVector3.at(-3, 0, 0), Transform.MIRROR_X), copies(symmetry, 3, 0, 0, 100));
        // Near the second only: its copy (at 53) is out of range of the first
        assertEquals(Map.of(BlockVector3.at(53, 0, 0), Transform.MIRROR_X), copies(symmetry, 47, 0, 0, 100));
        assertTrue(copies(symmetry, 25, 0, 0, 100).isEmpty());
    }

    @Test
    void copiesLandingTogetherAreCountedOnce() {
        // Same point and type twice: the second adds nothing
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, SymmetryType.STAR3D, 0, true)
                .withSecond(new Symmetry.Second(BlockVector3.ZERO, SymmetryType.STAR3D));
        assertEquals(SymmetryType.STAR3D.copies() - 1, copies(symmetry, 3, 5, 1, 100).size());
    }

    @Test
    void atMostAMegastarAndAStar3d() {
        assertTrue(Symmetry.combinable(SymmetryType.MEGASTAR, SymmetryType.STAR3D));
        assertTrue(Symmetry.combinable(SymmetryType.STAR3D, SymmetryType.MEGASTAR));
        assertTrue(Symmetry.combinable(SymmetryType.STAR3D, SymmetryType.STAR3D));
        assertTrue(Symmetry.combinable(SymmetryType.MEGASTAR, null));
        assertFalse(Symmetry.combinable(SymmetryType.MEGASTAR, SymmetryType.MEGASTAR));
        for (SymmetryType first : SymmetryType.values()) {
            for (SymmetryType second : SymmetryType.values()) {
                boolean twoMegastars = first == SymmetryType.MEGASTAR && second == SymmetryType.MEGASTAR;
                assertEquals(!twoMegastars, Symmetry.combinable(first, second), first + " + " + second);
            }
        }
    }

    @Test
    void megastarAndStar3dPlaceUpTo768Blocks() {
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, SymmetryType.MEGASTAR, 0, true)
                .withSecond(new Symmetry.Second(BlockVector3.at(200, 0, 0), SymmetryType.STAR3D));
        assertEquals(768, symmetry.copyCount());
        // Every copy of the megastar is copied by the star3d, some landing on others
        int count = copies(symmetry, 5, 7, 3, 1000).size();
        assertTrue(count > SymmetryType.MEGASTAR.copies() && count < 768, String.valueOf(count));
    }
    @Test
    void evenPointMirrorsBlocksInPairs() {
        // Point between x = 0 and x = 1
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, true, SymmetryType.PLANX, 0, true, null);
        assertEquals(Map.of(BlockVector3.at(1, 5, 2), Transform.MIRROR_X), copies(symmetry, 0, 5, 2, 100));
        assertEquals(Map.of(BlockVector3.at(-4, 5, 2), Transform.MIRROR_X), copies(symmetry, 5, 5, 2, 100));
    }

    @Test
    void evenPointCopiesWholeBlocksForEveryType() {
        for (SymmetryType type : SymmetryType.values()) {
            Symmetry symmetry = new Symmetry("world", BlockVector3.at(10, 64, -3), true, type, 0, true, null);
            // Every block lands on a block of its own: no copy on the block itself
            assertEquals(type.copies() - 1, copies(symmetry, 13, 70, 1, 100).size(), type.id());
        }
    }

    @Test
    void evenRangeHasTheBlocksOnEachSide() {
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, true, SymmetryType.PLANX, 2, true, null);
        assertTrue(symmetry.inRange(2, 0, 0, 100));
        assertTrue(symmetry.inRange(-1, 0, 0, 100));
        assertFalse(symmetry.inRange(3, 0, 0, 100));
        assertFalse(symmetry.inRange(-2, 0, 0, 100));
    }

    @Test
    void swapExchangesTheSymmetries() {
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, false, SymmetryType.PLANX, 7, true,
                new Symmetry.Second(BlockVector3.at(0, 0, 10), true, SymmetryType.STAR2D));
        Symmetry swapped = symmetry.swapped();
        assertEquals(new Symmetry("world", BlockVector3.at(0, 0, 10), true, SymmetryType.STAR2D, 7, true,
                new Symmetry.Second(BlockVector3.ZERO, false, SymmetryType.PLANX)), swapped);
        assertEquals(symmetry, swapped.swapped());
    }
    @Test
    void pausedSecondDoesNotCopy() {
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, false, SymmetryType.PLANX, 0, true,
                new Symmetry.Second(BlockVector3.at(0, 0, 10), false, SymmetryType.PLANZ, false));
        assertEquals(Map.of(BlockVector3.at(-3, 5, 2), Transform.MIRROR_X), copies(symmetry, 3, 5, 2, 100));
        assertEquals(SymmetryType.PLANX.copies(), symmetry.copyCount());
        // The pause stays with the second symmetry when swapped
        assertFalse(symmetry.swapped().second().enabled());
    }
}

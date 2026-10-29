package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryTypeTest {

    private static Set<BlockVector3> copies(SymmetryType type, int x, int y, int z) {
        Symmetry symmetry = new Symmetry("world", BlockVector3.ZERO, type, 0, true);
        Set<BlockVector3> positions = new HashSet<>();
        for (Symmetry.Copy copy : symmetry.copies(x, y, z, 100)) {
            positions.add(copy.position());
        }
        return positions;
    }

    @Test
    void countsTheCopies() {
        assertEquals(2, SymmetryType.SIMPLE2D.copies());
        assertEquals(2, SymmetryType.PLANX.copies());
        assertEquals(2, SymmetryType.PLANY.copies());
        assertEquals(2, SymmetryType.PLANZ.copies());
        assertEquals(4, SymmetryType.CROSS2D.copies());
        assertEquals(8, SymmetryType.CROSS3D.copies());
        assertEquals(8, SymmetryType.STAR2D.copies());
        assertEquals(16, SymmetryType.STAR3D.copies());
        assertEquals(48, SymmetryType.MEGASTAR.copies());
    }

    @Test
    void crossIsTwoPlanes() {
        assertEquals(Set.of(BlockVector3.at(-3, 1, 2), BlockVector3.at(3, 1, -2), BlockVector3.at(-3, 1, -2)),
                copies(SymmetryType.CROSS2D, 3, 1, 2));
    }

    @Test
    void starAddsTheDiagonals() {
        Set<BlockVector3> star = copies(SymmetryType.STAR2D, 3, 1, 2);
        assertEquals(7, star.size());
        assertTrue(star.contains(BlockVector3.at(2, 1, 3)));
        assertTrue(star.contains(BlockVector3.at(-2, 1, -3)));
        assertTrue(star.stream().allMatch(p -> p.y() == 1));
    }

    @Test
    void megastarGivesEveryPermutation() {
        Set<BlockVector3> mega = copies(SymmetryType.MEGASTAR, 1, 2, 3);
        assertEquals(47, mega.size());
        assertTrue(mega.contains(BlockVector3.at(3, -1, 2)));
    }

    @Test
    void simple2dIsAHalfTurn() {
        assertEquals(Set.of(BlockVector3.at(-3, 5, -2)), copies(SymmetryType.SIMPLE2D, 3, 5, 2));
    }

    @Test
    void skipsCopiesOnTheBlockItself() {
        assertEquals(Set.of(BlockVector3.at(0, 0, -2)), copies(SymmetryType.CROSS2D, 0, 0, 2));
        assertTrue(copies(SymmetryType.MEGASTAR, 0, 0, 0).isEmpty());
    }

    @Test
    void staysInRange() {
        Symmetry symmetry = new Symmetry("world", BlockVector3.at(10, 64, 10), SymmetryType.PLANX, 5, true);
        assertTrue(symmetry.copies(16, 64, 10, 100).isEmpty());
        assertEquals(List.of(BlockVector3.at(5, 64, 10)),
                symmetry.copies(15, 64, 10, 100).stream().map(Symmetry.Copy::position).toList());
        // The configured maximum wins over the player's range
        assertTrue(symmetry.copies(15, 64, 10, 3).isEmpty());
    }

    @Test
    void findsTypesById() {
        assertEquals(SymmetryType.STAR3D, SymmetryType.fromId("Star3D"));
        assertNull(SymmetryType.fromId("triangle"));
    }

    @Test
    void transformsComposeAndInvert() {
        for (Transform transform : SymmetryType.MEGASTAR.transforms()) {
            assertTrue(transform.inverse().after(transform).isIdentity(), transform.toString());
        }
        Set<Integer> codes = new HashSet<>();
        SymmetryType.MEGASTAR.transforms().forEach(t -> codes.add(t.code()));
        assertEquals(47, codes.size());
    }
}

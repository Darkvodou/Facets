package fr.darkvodou.facets.mask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionTest {

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;

    private static final int STONE = Occlusion.ALL_FACES;
    private static final int AIR = Occlusion.EMPTY;
    private static final int GLASS = 0;
    private static final int BOTTOM_SLAB = 1 << DOWN;
    // Stairs facing south: their back is the north face.
    private static final int STAIRS = 1 << DOWN | 1 << NORTH;

    /** Stone surrounded by stone, except the given neighbor on the given face. */
    private static boolean occluded(int face, int neighbor) {
        return Occlusion.isOccluded(STONE, f -> f == face ? neighbor : STONE);
    }

    @Test
    void noOcclusionBlocks() {
        assertTrue(Occlusion.isNoOcclusion(GLASS));
        assertTrue(Occlusion.isNoOcclusion(BOTTOM_SLAB));
        assertTrue(Occlusion.isNoOcclusion(STAIRS));
        assertFalse(Occlusion.isNoOcclusion(STONE));
        assertFalse(Occlusion.isNoOcclusion(AIR));
    }

    @Test
    void faceSeenThroughOpenSide() {
        assertTrue(occluded(UP, GLASS));
        // Slab on top of the block: its full bottom covers the block.
        assertFalse(occluded(UP, BOTTOM_SLAB));
        // Slab under the block: its top is open.
        assertTrue(occluded(DOWN, BOTTOM_SLAB));
    }

    @Test
    void faceHiddenByBackOfStairs() {
        // Stairs south of the block show it their north back.
        assertFalse(occluded(SOUTH, STAIRS));
        // Stairs north of the block show it their open south side.
        assertTrue(occluded(NORTH, STAIRS));
    }

    @Test
    void onlyFullBlocksAreOccluded() {
        assertFalse(occluded(UP, AIR));
        assertFalse(occluded(UP, STONE));
        assertFalse(Occlusion.isOccluded(BOTTOM_SLAB, f -> GLASS));
        assertFalse(Occlusion.isOccluded(AIR, f -> GLASS));
    }
}

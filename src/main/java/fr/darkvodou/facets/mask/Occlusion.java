package fr.darkvodou.facets.mask;

import java.util.function.IntUnaryOperator;

/**
 * Occlusion rules on per-state flags: bits 0-5 are the faces (down, up, north, south, west, east)
 * that are full and opaque, {@link #EMPTY} marks air and liquids.
 */
final class Occlusion {

    static final int FACES = 6;
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};

    static final int ALL_FACES = 0x3F;
    static final int EMPTY = 0x40;

    private Occlusion() {
    }

    static int opposite(int face) {
        return face ^ 1;
    }

    /** {@code #noocclusion}: a block that lets the blocks behind it be seen. */
    static boolean isNoOcclusion(int flags) {
        return (flags & EMPTY) == 0 && (flags & ALL_FACES) != ALL_FACES;
    }

    /** Whether the given face of a block is seen through its neighbor on that side. */
    static boolean showsFace(int face, int neighborFlags) {
        return isNoOcclusion(neighborFlags) && (neighborFlags & (1 << opposite(face))) == 0;
    }

    /** {@code #occluded}: a full opaque block with a face seen through a {@code #noocclusion} neighbor. */
    static boolean isOccluded(int flags, IntUnaryOperator neighborFlags) {
        if (flags != ALL_FACES) {
            return false;
        }
        for (int face = 0; face < FACES; face++) {
            if (showsFace(face, neighborFlags.applyAsInt(face))) {
                return true;
            }
        }
        return false;
    }
}

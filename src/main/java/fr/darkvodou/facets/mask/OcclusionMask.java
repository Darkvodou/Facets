package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.AbstractExtentMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * {@code #noocclusion} or {@code #occluded}, see {@link Occlusion}.
 */
final class OcclusionMask extends AbstractExtentMask {

    private final byte[] flags;
    private final boolean occluded;

    OcclusionMask(Extent extent, byte[] flags, boolean occluded) {
        super(extent);
        this.flags = flags;
        this.occluded = occluded;
    }

    @Override
    public boolean test(BlockVector3 position) {
        return test(getExtent(), position);
    }

    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        int own = flags(extent, x, y, z);
        if (!occluded) {
            return Occlusion.isNoOcclusion(own);
        }
        return Occlusion.isOccluded(own, face ->
                flags(extent, x + Occlusion.DX[face], y + Occlusion.DY[face], z + Occlusion.DZ[face]));
    }

    private int flags(Extent extent, int x, int y, int z) {
        return flags[extent.getBlock(x, y, z).getOrdinal()];
    }

    @Override
    public Mask copy() {
        return new OcclusionMask(getExtent(), flags, occluded);
    }
}

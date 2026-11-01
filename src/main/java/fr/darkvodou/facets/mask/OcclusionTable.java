package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

/**
 * {@link Occlusion} flags of every block state, indexed by FAWE ordinal.
 */
final class OcclusionTable {

    private static final BlockFace[] FACES = {
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    private static byte[] flags;

    private OcclusionTable() {
    }

    static synchronized byte[] get() {
        // Computed on first use, once the block registry is fully populated.
        if (flags == null) {
            BlockState[] states = BlockTypesCache.states;
            byte[] table = new byte[states.length];
            for (int i = 0; i < states.length; i++) {
                table[i] = (byte) (states[i] == null ? Occlusion.EMPTY : flags(BukkitAdapter.adapt(states[i])));
            }
            flags = table;
        }
        return flags;
    }

    private static int flags(BlockData data) {
        Material material = data.getMaterial();
        if (material.isAir() || material == Material.WATER || material == Material.LAVA
                || material == Material.BUBBLE_COLUMN) {
            return Occlusion.EMPTY;
        }
        // Glass, leaves, barriers, trapdoors... never hide anything.
        if (!data.isOccluding()) {
            return 0;
        }
        int faces = 0;
        for (int face = 0; face < FACES.length; face++) {
            if (data.isFaceSturdy(FACES[face], BlockSupport.FULL)) {
                faces |= 1 << face;
            }
        }
        return faces;
    }
}

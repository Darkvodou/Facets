package fr.darkvodou.facets.cover;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The blocks covering the faces of their neighbors (glow lichen, sculk veins, resin clumps, vines), with a boolean
 * property per face, and how to make them cover exactly the blocks next to them.
 */
final class Cover {

    private static final Set<String> COVERS = Set.of(
            "minecraft:glow_lichen", "minecraft:sculk_vein", "minecraft:resin_clump", "minecraft:vine");

    /**
     * The faces of a cover: its property, where the neighbor is and which face of the neighbor holds it.
     */
    private enum Face {
        NORTH("north", BlockVector3.UNIT_MINUS_Z, BlockFace.SOUTH),
        SOUTH("south", BlockVector3.UNIT_Z, BlockFace.NORTH),
        WEST("west", BlockVector3.UNIT_MINUS_X, BlockFace.EAST),
        EAST("east", BlockVector3.UNIT_X, BlockFace.WEST),
        UP("up", BlockVector3.UNIT_Y, BlockFace.DOWN),
        DOWN("down", BlockVector3.UNIT_MINUS_Y, BlockFace.UP);

        final String property;
        final BlockVector3 offset;
        final BlockFace holding;

        Face(String property, BlockVector3 offset, BlockFace holding) {
            this.property = property;
            this.offset = offset;
            this.holding = holding;
        }

        boolean side() {
            return this != UP && this != DOWN;
        }
    }

    // Sturdy faces of each block state, a bit per BlockFace ordinal
    private static final Map<BlockState, Integer> STURDY = new ConcurrentHashMap<>();

    private Cover() {
    }

    static boolean isCover(BlockState state) {
        return COVERS.contains(state.getBlockType().id());
    }

    private static boolean isVine(BlockState state) {
        return state.getBlockType() == BlockTypes.VINE;
    }

    private static boolean holds(BlockState neighbor, BlockFace face) {
        int faces = STURDY.computeIfAbsent(neighbor, state -> {
            if (state.getBlockType().getMaterial().isAir()) {
                return 0;
            }
            var data = BukkitAdapter.adapt(state);
            int bits = 0;
            for (Face f : Face.values()) {
                if (data.isFaceSturdy(f.holding, BlockSupport.FULL)) {
                    bits |= 1 << f.holding.ordinal();
                }
            }
            return bits;
        });
        return (faces & 1 << face.ordinal()) != 0;
    }

    @SuppressWarnings("unchecked")
    private static Property<Boolean> property(BlockState state, String name) {
        return (Property<Boolean>) state.getBlockType().getPropertyMap().get(name);
    }

    /**
     * A hanging vine keeps a side face when the vine above has it, up to the vine holding on a block.
     */
    private static boolean hangs(BlockVector3 position, Face face, Function<BlockVector3, BlockState> world) {
        BlockVector3 above = position.add(BlockVector3.UNIT_Y);
        for (int i = 0; i < 512; i++, above = above.add(BlockVector3.UNIT_Y)) {
            BlockState state = world.apply(above);
            Property<Boolean> property = property(state, face.property);
            if (!isVine(state) || property == null || !state.getState(property)) {
                return false;
            }
            if (holds(world.apply(above.add(face.offset)), face.holding)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param world the blocks around, by position
     * @return the cover with a face on each block next to it that can hold it and none elsewhere, air (or water when
     * waterlogged) when no face is left
     */
    static BlockState fix(BlockState state, BlockVector3 position, Function<BlockVector3, BlockState> world) {
        boolean vine = isVine(state);
        BlockState result = state;
        boolean any = false;
        for (Face face : Face.values()) {
            Property<Boolean> property = property(state, face.property);
            if (property == null) {
                continue;
            }
            boolean on = holds(world.apply(position.add(face.offset)), face.holding)
                    || vine && face.side() && state.getState(property) && hangs(position, face, world);
            result = result.with(property, on);
            any |= on;
        }
        if (any) {
            return result;
        }
        Property<Boolean> waterlogged = property(state, "waterlogged");
        return waterlogged != null && state.getState(waterlogged)
                ? BlockTypes.WATER.getDefaultState() : BlockTypes.AIR.getDefaultState();
    }
}

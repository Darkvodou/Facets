package fr.darkvodou.facets.cover;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The blocks held by the faces of their neighbors, and how to make them hold on the blocks next to them:
 * <ul>
 * <li>glow lichen, sculk veins, resin clumps and vines cover every face next to them;</li>
 * <li>pale moss carpets lie on the block below and climb the walls next to them, when they can hold them;</li>
 * <li>ladders, amethyst clusters and buds, shelves, trapdoors, banners, coral fans and buttons hold on one face (only
 * the walls for ladders, shelves and banners, never the ceiling for coral fans): they turn to the only block next to
 * them that holds them, and are removed when several do (or turned to one of them at random) or none does.</li>
 * </ul>
 * The excluded faces never hold anything: -n forbids holding on the block to the south (the blocks facing north).
 */
final class Cover {

    private enum Kind {
        MULTIFACE, MOSS_CARPET, LADDER, AMETHYST, SHELF, TRAPDOOR, BANNER, WALL_BANNER, CORAL_FAN, CORAL_WALL_FAN, BUTTON, NONE
    }

    private static final Set<String> MULTIFACES = Set.of(
            "minecraft:glow_lichen", "minecraft:sculk_vein", "minecraft:resin_clump", "minecraft:vine");
    private static final Set<String> AMETHYSTS = Set.of("minecraft:amethyst_cluster",
            "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud", "minecraft:large_amethyst_bud");

    /**
     * The faces around a block: its property, where the neighbor is and which face of the neighbor holds it.
     */
    enum Face {
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

        Face opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }

        /**
         * The flag excluding this face: the way the blocks held there face, away from it (-u for the floor, where
         * stairs stand the right way up, -n for the wall to the south).
         */
        char flag() {
            return opposite().property.charAt(0);
        }

        /**
         * @return the face of a direction flag (n, s, e, w, u, d), null for another letter
         */
        static Face ofFlag(char letter) {
            for (Face face : values()) {
                if (face.flag() == letter) {
                    return face;
                }
            }
            return null;
        }
    }

    // Sturdy faces of each block state, a bit per BlockFace ordinal
    private static final Map<BlockState, Integer> STURDY = new ConcurrentHashMap<>();
    private static final Map<BlockType, Kind> KINDS = new ConcurrentHashMap<>();

    private Cover() {
    }

    /**
     * The blocks around, by position, and the faces that never hold anything.
     */
    private record Around(Function<BlockVector3, BlockState> world, Set<Face> excluded) {

        BlockState at(BlockVector3 position) {
            return world.apply(position);
        }

        /**
         * Whether the neighbor of the block at this position, on this face, can hold it. The blocks fixed here never
         * do: they may turn (the back of a ladder is a full face, so a row of ladders would hold each other).
         */
        boolean holds(BlockVector3 position, Face face) {
            if (excluded.contains(face)) {
                return false;
            }
            BlockState neighbor = world.apply(position.add(face.offset));
            return !isCover(neighbor) && Cover.holds(neighbor, face.holding);
        }
    }

    private static Kind kind(BlockState state) {
        return KINDS.computeIfAbsent(state.getBlockType(), type -> {
            String id = type.id();
            if (MULTIFACES.contains(id)) {
                return Kind.MULTIFACE;
            }
            if (AMETHYSTS.contains(id)) {
                return Kind.AMETHYST;
            }
            if (id.equals("minecraft:pale_moss_carpet")) {
                return Kind.MOSS_CARPET;
            }
            if (id.equals("minecraft:ladder")) {
                return Kind.LADDER;
            }
            if (id.endsWith("_shelf")) {
                return Kind.SHELF;
            }
            if (id.endsWith("_trapdoor")) {
                return Kind.TRAPDOOR;
            }
            if (id.endsWith("_button")) {
                return Kind.BUTTON;
            }
            if (id.endsWith("_coral_wall_fan")) {
                return Kind.CORAL_WALL_FAN;
            }
            if (id.endsWith("_coral_fan")) {
                return Kind.CORAL_FAN;
            }
            if (id.endsWith("_wall_banner")) {
                return Kind.WALL_BANNER;
            }
            if (id.endsWith("_banner")) {
                return Kind.BANNER;
            }
            return Kind.NONE;
        });
    }

    static boolean isCover(BlockState state) {
        return kind(state) != Kind.NONE;
    }

    private static boolean isVine(BlockState state) {
        return state.getBlockType() == BlockTypes.VINE;
    }

    private static boolean isAir(BlockState state) {
        return state.getBlockType().getMaterial().isAir();
    }

    private static boolean holds(BlockState neighbor, BlockFace face) {
        int faces = STURDY.computeIfAbsent(neighbor, state -> {
            if (isAir(state)) {
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
     * @return the value of the property in lower case, null when the block doesn't have it
     */
    private static String get(BlockState state, String name) {
        Property<?> property = state.getBlockType().getPropertyMap().get(name);
        return property == null ? null : String.valueOf(state.getState(property)).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static BlockState with(BlockState state, String name, String value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        return property == null ? state : state.with(property, property.getValueFor(value));
    }

    /**
     * @return air, or water when the block is waterlogged
     */
    private static BlockState removed(BlockState state) {
        Property<Boolean> waterlogged = property(state, "waterlogged");
        return waterlogged != null && state.getState(waterlogged)
                ? BlockTypes.WATER.getDefaultState() : BlockTypes.AIR.getDefaultState();
    }

    /**
     * @param world    the blocks around, by position
     * @param random   picks one of the placements when several are as good, null removes the block
     * @param excluded the faces that never hold anything
     * @return the block holding on the blocks next to it, air (or water when waterlogged) when nothing can hold it
     */
    static BlockState fix(BlockState state, BlockVector3 position, Function<BlockVector3, BlockState> world,
                          Random random, Set<Face> excluded) {
        Around around = new Around(world, excluded);
        return switch (kind(state)) {
            case NONE -> state;
            case MULTIFACE -> fixMultiface(state, position, around);
            case MOSS_CARPET -> fixMossCarpet(state, position, around);
            default -> fixPlaced(state, position, around, random);
        };
    }

    /**
     * A hanging vine keeps a side face when the vine above has it, up to the vine holding on a block.
     */
    private static boolean hangs(BlockVector3 position, Face face, Around around) {
        BlockVector3 above = position.add(BlockVector3.UNIT_Y);
        for (int i = 0; i < 512; i++, above = above.add(BlockVector3.UNIT_Y)) {
            BlockState state = around.at(above);
            Property<Boolean> property = property(state, face.property);
            if (!isVine(state) || property == null || !state.getState(property)) {
                return false;
            }
            if (around.holds(above, face)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Glow lichen, sculk veins, resin clumps and vines: a face on each block next to them that can hold them.
     */
    private static BlockState fixMultiface(BlockState state, BlockVector3 position,
                                           Around around) {
        boolean vine = isVine(state);
        BlockState result = state;
        boolean any = false;
        for (Face face : Face.values()) {
            Property<Boolean> property = property(state, face.property);
            if (property == null) {
                continue;
            }
            boolean on = around.holds(position, face)
                    || vine && face.side() && state.getState(property) && hangs(position, face, around);
            result = result.with(property, on);
            any |= on;
        }
        return any ? result : removed(state);
    }

    /**
     * Pale moss carpets: the carpet lies on the block below when it can hold it, climbs the whole height of the walls
     * next to it, and is removed when it holds on nothing.
     */
    private static BlockState fixMossCarpet(BlockState state, BlockVector3 position,
                                            Around around) {
        boolean bottom = around.holds(position, Face.DOWN);
        BlockState result = with(state, "bottom", String.valueOf(bottom));
        boolean any = bottom;
        for (Face face : Face.values()) {
            if (face.side()) {
                boolean wall = around.holds(position, face);
                result = with(result, face.property, wall ? "tall" : "none");
                any |= wall;
            }
        }
        return any ? result : BlockTypes.AIR.getDefaultState();
    }

    /**
     * Whether the neighbor on this face holds a block holding on one face: a full face, or a solid block for banners.
     * Ladders, shelves and banners only hold on the walls, so the floor and the ceiling don't count for them; coral fans
     * never hang from the ceiling.
     */
    private static boolean touches(Kind kind, BlockVector3 position, Face face,
                                   Around around) {
        return switch (kind) {
            case BANNER, WALL_BANNER -> {
                BlockState neighbor = around.at(position.add(face.offset));
                yield face.side() && !around.excluded().contains(face) && !isCover(neighbor) && neighbor.getBlockType().getMaterial().isSolid();
            }
            case LADDER, SHELF -> face.side() && around.holds(position, face);
            case CORAL_FAN, CORAL_WALL_FAN -> face != Face.UP && around.holds(position, face);
            default -> around.holds(position, face);
        };
    }

    /**
     * @return the block holding on this face, null when it can't
     */
    private static BlockState place(Kind kind, BlockState state, Face face) {
        String facing = face.opposite().property;
        switch (kind) {
            case TRAPDOOR -> {
                if (face.side()) {
                    return with(with(state, "open", "true"), "facing", facing);
                }
                return with(with(state, "open", "false"), "half", face == Face.UP ? "top" : "bottom");
            }
            case BANNER, WALL_BANNER -> {
                // Standing banners become wall banners
                String id = state.getBlockType().id();
                String color = id.substring(0, id.length() - (kind == Kind.BANNER ? "_banner" : "_wall_banner").length());
                BlockType type = BlockTypes.get(color + "_wall_banner");
                return type == null ? null : with(type.getDefaultState(), "facing", facing);
            }
            case BUTTON -> {
                if (face.side()) {
                    return with(with(state, "face", "wall"), "facing", facing);
                }
                return with(state, "face", face == Face.UP ? "ceiling" : "floor");
            }
            case CORAL_FAN, CORAL_WALL_FAN -> {
                // On the floor a coral fan, on a wall a coral wall fan, still waterlogged or not
                String id = state.getBlockType().id();
                String coral = id.substring(0, id.length() - (kind == Kind.CORAL_FAN ? "_fan" : "_wall_fan").length());
                BlockType type = BlockTypes.get(coral + (face == Face.DOWN ? "_fan" : "_wall_fan"));
                if (type == null) {
                    return null;
                }
                String waterlogged = String.valueOf("true".equals(get(state, "waterlogged")));
                BlockState placed = with(type.getDefaultState(), "waterlogged", waterlogged);
                return face == Face.DOWN ? placed : with(placed, "facing", facing);
            }
            default -> {
                return with(state, "facing", facing);
            }
        }
    }

    /**
     * Ladders, amethyst clusters and buds, shelves, trapdoors, banners, coral fans and buttons, whatever their current placement: turned to
     * the face next to them when only one holds them. Removed when several do (a corner), unless random picks one of
     * them, and when none does.
     */
    private static BlockState fixPlaced(BlockState state, BlockVector3 position,
                                        Around around, Random random) {
        Kind kind = kind(state);
        List<BlockState> placements = new ArrayList<>();
        for (Face face : Face.values()) {
            if (touches(kind, position, face, around)) {
                BlockState placed = place(kind, state, face);
                if (placed != null) {
                    placements.add(placed);
                }
            }
        }
        if (placements.isEmpty() || placements.size() > 1 && random == null) {
            return removed(state);
        }
        return placements.get(random == null ? 0 : random.nextInt(placements.size()));
    }
}

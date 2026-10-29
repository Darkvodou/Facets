package fr.darkvodou.moretools.symmetry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockStateMirrorTest {

    private static final List<String> HORIZONTAL = List.of("north", "south", "east", "west");
    private static final Map<String, List<String>> STAIRS = Map.of(
            "facing", HORIZONTAL,
            "half", List.of("top", "bottom"),
            "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
            "waterlogged", List.of("true", "false"));

    private static Map<String, String> state(String... pairs) {
        Map<String, String> state = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            state.put(pairs[i], pairs[i + 1]);
        }
        return state;
    }

    private static Map<String, String> stairs(String facing, String half, String shape) {
        return state("facing", facing, "half", half, "shape", shape, "waterlogged", "false");
    }

    private static Map<String, String> apply(Map<String, String> state, Map<String, List<String>> allowed,
                                             Transform transform) {
        return BlockStateMirror.transform(state, allowed, transform);
    }

    @Test
    void mirrorsStairsAcrossTheirFacing() {
        // Facing north, outer left: its corner is north west. Mirrored along z: south west, facing south outer right
        assertEquals(stairs("south", "bottom", "outer_right"),
                apply(stairs("north", "bottom", "outer_left"), STAIRS, Transform.MIRROR_Z));
        assertEquals(stairs("south", "bottom", "inner_left"),
                apply(stairs("north", "bottom", "inner_right"), STAIRS, Transform.MIRROR_Z));
    }

    @Test
    void mirrorsStairsAlongTheirFacing() {
        // Facing east, outer left: corner north east. Mirrored along z: south east, still facing east, outer right
        assertEquals(stairs("east", "bottom", "outer_right"),
                apply(stairs("east", "bottom", "outer_left"), STAIRS, Transform.MIRROR_Z));
        assertEquals(stairs("west", "top", "inner_right"),
                apply(stairs("east", "top", "inner_left"), STAIRS, Transform.MIRROR_X));
    }

    @Test
    void mirrorsStairsOnTheDiagonal() {
        // Facing east, outer left: corner north east (1, -1). On x = z: (-1, 1), south west, facing south outer right
        assertEquals(stairs("south", "bottom", "outer_right"),
                apply(stairs("east", "bottom", "outer_left"), STAIRS, Transform.SWAP_XZ));
    }

    @Test
    void turnsStairsWithoutChangingTheirShape() {
        assertEquals(stairs("west", "bottom", "outer_left"),
                apply(stairs("east", "bottom", "outer_left"), STAIRS, Transform.HALF_TURN_Y));
    }

    @Test
    void flipsStairsUpsideDown() {
        assertEquals(stairs("east", "top", "inner_left"),
                apply(stairs("east", "bottom", "inner_left"), STAIRS, Transform.MIRROR_Y));
    }

    @Test
    void keepsStairsHorizontalInThreeDimensions() {
        // Turned so that east becomes up: stairs cannot face up, the facing stays
        Transform eastToUp = Transform.SWAP_XY;
        assertEquals("east", apply(stairs("east", "bottom", "straight"), STAIRS, eastToUp).get("facing"));
    }

    @Test
    void movesConnections() {
        Map<String, List<String>> fence = Map.of("north", List.of("true", "false"), "south", List.of("true", "false"),
                "east", List.of("true", "false"), "west", List.of("true", "false"));
        assertEquals(state("north", "false", "south", "false", "east", "false", "west", "true"),
                apply(state("north", "false", "south", "false", "east", "true", "west", "false"), fence, Transform.MIRROR_X));
        assertEquals(state("north", "false", "south", "true", "east", "false", "west", "false"),
                apply(state("north", "false", "south", "false", "east", "true", "west", "false"), fence, Transform.SWAP_XZ));
    }

    @Test
    void flipsSlabsAndDoors() {
        assertEquals("top", apply(state("type", "bottom"), Map.of(), Transform.MIRROR_Y).get("type"));
        assertEquals("double", apply(state("type", "double"), Map.of(), Transform.MIRROR_Y).get("type"));
        assertEquals("bottom", apply(state("type", "bottom"), Map.of(), Transform.MIRROR_X).get("type"));
        Map<String, String> door = state("facing", "north", "half", "lower", "hinge", "left", "open", "false");
        assertEquals(state("facing", "north", "half", "lower", "hinge", "right", "open", "false"),
                apply(door, Map.of(), Transform.MIRROR_X));
        assertEquals(state("facing", "north", "half", "upper", "hinge", "left", "open", "false"),
                apply(door, Map.of(), Transform.MIRROR_Y));
    }

    @Test
    void swapsChestHalves() {
        assertEquals(state("facing", "south", "type", "right"),
                apply(state("facing", "north", "type", "left"), Map.of(), Transform.MIRROR_Z));
    }

    @Test
    void turnsAxes() {
        assertEquals("z", apply(state("axis", "x"), Map.of(), Transform.SWAP_XZ).get("axis"));
        assertEquals("x", apply(state("axis", "x"), Map.of(), Transform.MIRROR_X).get("axis"));
        assertEquals("y", apply(state("axis", "x"), Map.of(), Transform.SWAP_XY).get("axis"));
    }

    @Test
    void turnsRotations() {
        // 0 south, 4 west, 8 north, 12 east
        assertEquals("12", apply(state("rotation", "4"), Map.of(), Transform.MIRROR_X).get("rotation"));
        assertEquals("1", apply(state("rotation", "15"), Map.of(), Transform.MIRROR_X).get("rotation"));
        assertEquals("8", apply(state("rotation", "0"), Map.of(), Transform.MIRROR_Z).get("rotation"));
        assertEquals("12", apply(state("rotation", "0"), Map.of(), Transform.SWAP_XZ).get("rotation"));
        assertEquals("10", apply(state("rotation", "2"), Map.of(), Transform.SWAP_XZ).get("rotation"));
    }

    @Test
    void turnsRails() {
        Map<String, List<String>> rail = Map.of("shape", List.of("north_south", "east_west", "ascending_east",
                "ascending_west", "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east"));
        assertEquals("south_west", apply(state("shape", "south_east"), rail, Transform.MIRROR_X).get("shape"));
        assertEquals("north_west", apply(state("shape", "south_east"), rail, Transform.HALF_TURN_Y).get("shape"));
        assertEquals("east_west", apply(state("shape", "north_south"), rail, Transform.SWAP_XZ).get("shape"));
        assertEquals("ascending_south", apply(state("shape", "ascending_east"), rail, Transform.SWAP_XZ).get("shape"));
        assertEquals("ascending_west", apply(state("shape", "ascending_east"), rail, Transform.MIRROR_Y).get("shape"));
        // Powered rails have no corners
        Map<String, List<String>> powered = Map.of("shape", List.of("north_south", "east_west"));
        assertEquals("north_south", apply(state("shape", "north_south"), powered, Transform.MIRROR_X).get("shape"));
    }

    @Test
    void flipsAttachedBlocks() {
        assertEquals("ceiling", apply(state("face", "floor"), Map.of(), Transform.MIRROR_Y).get("face"));
        assertEquals("true", apply(state("hanging", "false"), Map.of(), Transform.MIRROR_Y).get("hanging"));
        assertEquals("down", apply(state("facing", "up"), Map.of(), Transform.MIRROR_Y).get("facing"));
        assertEquals("down", apply(state("vertical_direction", "up"), Map.of(), Transform.MIRROR_Y).get("vertical_direction"));
        assertEquals("south_down", apply(state("orientation", "north_up"), Map.of(), Transform.MIRROR_Y.after(Transform.MIRROR_Z))
                .get("orientation"));
    }

    @Test
    void keepsValuesTheBlockCannotTake() {
        Map<String, List<String>> hopper = Map.of("facing", List.of("down", "north", "south", "east", "west"));
        assertEquals("down", apply(state("facing", "down"), hopper, Transform.MIRROR_Y).get("facing"));
    }
}

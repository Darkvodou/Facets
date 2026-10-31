package fr.darkvodou.facets.symmetry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the properties of a block state (facing=east, shape=outer_left...) the way a transform turns the block.
 * Works on the names and values as written in Minecraft, so it does not depend on a block registry.
 * Stairs are turned by their shape, so that they follow every turn they can take, on their side too.
 * A block that cannot be turned that way (a slab on its side, outer stairs on their side...) has no result:
 * it is placed as it was.
 */
public final class BlockStateMirror {

    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");
    private static final int ROTATIONS = 16;
    // Returned for a value the block cannot turn to
    private static final String IMPOSSIBLE = "\0impossible";
    // Properties that only make sense while up stays vertical, for each value
    private static final Set<String> VERTICAL = Set.of("half", "hanging");
    private static final List<String> HORIZONTAL = List.of("north", "south", "east", "west");
    private static final List<String> STAIR_SHAPES = List.of("straight", "inner_left", "inner_right", "outer_left",
            "outer_right");

    private BlockStateMirror() {
    }

    /**
     * @param state   the property values of the block
     * @param allowed the values each property can take
     * @return the new property values, with the same properties, or null if the block cannot be turned that way
     */
    @Nullable
    public static Map<String, String> transform(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                                Transform transform) {
        Map<String, String> result = new LinkedHashMap<>(state);
        if (transform.isIdentity()) {
            return result;
        }
        if (isStairs(state)) {
            return stairs(state, allowed, transform);
        }
        boolean flipY = transform.flipsVertical();
        boolean swapHands = transform.swapsHandedness();
        boolean vertical = transform.keepsVertical();

        // Connections to each side (fences, walls, panes, vines, mushroom blocks, redstone...)
        for (String face : FACES) {
            if (!state.containsKey(face)) {
                continue;
            }
            String from = transform.inverse().direction(face);
            if (from != null && state.containsKey(from)) {
                if (!set(result, allowed, face, state.get(from))) {
                    return null;
                }
            } else if (!vertical) {
                // A side turned from one the block has no connection to: a fence on its side
                return null;
            }
        }

        for (Map.Entry<String, String> entry : state.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (!vertical && (VERTICAL.contains(name) || isVertical(name, value))) {
                return null;
            }
            String transformed = switch (name) {
                // Direction values: facing, vertical_direction (pointed dripstone)
                case "facing", "vertical_direction" -> transform.direction(value);
                case "axis" -> transform.axis(value);
                case "rotation" -> rotation(value, transform);
                case "orientation" -> orientation(value, transform);
                case "half" -> flipY ? swap(value, "top", "bottom", "upper", "lower") : value;
                // Slabs (top / bottom) and chests (left / right)
                case "type" -> swap(swap(value, flipY, "top", "bottom"), swapHands, "left", "right");
                case "shape" -> shape(value, transform, flipY);
                case "hinge" -> swap(value, swapHands, "left", "right");
                // Buttons, levers, grindstones (face) and bells (attachment)
                case "face", "attachment" -> swap(value, flipY, "floor", "ceiling");
                // Lanterns, mangrove propagules
                case "hanging" -> swap(value, flipY, "true", "false");
                default -> value;
            };
            if (IMPOSSIBLE.equals(transformed)) {
                return null;
            }
            if (transformed != null && !transformed.equals(value) && !set(result, allowed, name, transformed)) {
                return null;
            }
        }
        return result;
    }

    private static boolean isStairs(Map<String, String> state) {
        String shape = state.get("shape");
        return state.containsKey("facing") && shape != null && STAIR_SHAPES.contains(shape)
                && ("top".equals(state.get("half")) || "bottom".equals(state.get("half")));
    }

    /**
     * Turns stairs by their shape: the eighths of the block they fill are turned, then the stairs filling the same
     * eighths are looked for, the facing turned by the transform first.
     *
     * @return null if no stairs fill these eighths (outer stairs on their side)
     */
    @Nullable
    private static Map<String, String> stairs(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                              Transform transform) {
        int[] facing = Transform.Directions.vector(state.get("facing"));
        if (facing == null || facing[1] != 0) {
            return null;
        }
        int turned = 0;
        for (int eighth : eighths(state.get("facing"), state.get("half"), state.get("shape"))) {
            int[] v = transform.apply(sign(eighth, 0), sign(eighth, 1), sign(eighth, 2));
            turned |= 1 << eighth(v[0], v[1], v[2]);
        }
        String preferred = transform.direction(state.get("facing"));
        List<String> facings = new ArrayList<>(HORIZONTAL);
        if (facings.remove(preferred)) {
            facings.add(0, preferred);
        }
        for (String newFacing : facings) {
            for (String half : List.of("bottom", "top")) {
                for (String shape : STAIR_SHAPES) {
                    if (mask(eighths(newFacing, half, shape)) != turned) {
                        continue;
                    }
                    Map<String, String> result = new LinkedHashMap<>(state);
                    if (set(result, allowed, "facing", newFacing) && set(result, allowed, "half", half)
                            && set(result, allowed, "shape", shape)) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The eighths of the block filled by stairs, numbered by {@link #eighth}. Left is to the left of someone going
     * up the stairs, whatever their half.
     */
    static List<Integer> eighths(String facing, String half, String shape) {
        int[] f = Transform.Directions.vector(facing);
        // Turning the facing a quarter to the left: north to west
        int leftX = f[2];
        int leftZ = -f[0];
        int slab = half.equals("bottom") ? -1 : 1;
        List<Integer> eighths = new ArrayList<>();
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                eighths.add(eighth(x, slab, z));
                boolean front = x * f[0] + z * f[2] > 0;
                boolean left = x * leftX + z * leftZ > 0;
                boolean step = switch (shape) {
                    case "inner_left" -> front || left;
                    case "inner_right" -> front || !left;
                    case "outer_left" -> front && left;
                    case "outer_right" -> front && !left;
                    default -> front;
                };
                if (step) {
                    eighths.add(eighth(x, -slab, z));
                }
            }
        }
        return eighths;
    }

    private static int mask(List<Integer> eighths) {
        int mask = 0;
        for (int eighth : eighths) {
            mask |= 1 << eighth;
        }
        return mask;
    }

    // An eighth of the block, from the signs of its center: one bit per axis
    private static int eighth(int x, int y, int z) {
        return (x > 0 ? 1 : 0) | (y > 0 ? 2 : 0) | (z > 0 ? 4 : 0);
    }

    private static int sign(int eighth, int axis) {
        return (eighth >> axis & 1) == 1 ? 1 : -1;
    }

    /**
     * Whether the value says what is above or below the block: a top slab, a button on the floor.
     */
    private static boolean isVertical(String name, String value) {
        return switch (name) {
            case "type" -> value.equals("top") || value.equals("bottom");
            case "face", "attachment" -> value.equals("floor") || value.equals("ceiling");
            default -> false;
        };
    }

    /**
     * @return false if the block cannot take the value
     */
    private static boolean set(Map<String, String> result, Map<String, ? extends Collection<String>> allowed, String name,
                               String value) {
        Collection<String> values = allowed.get(name);
        if (values != null && !values.contains(value)) {
            return false;
        }
        result.put(name, value);
        return true;
    }

    private static String swap(String value, boolean apply, String a, String b) {
        return apply ? swap(value, a, b) : value;
    }

    private static String swap(String value, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (value.equals(pairs[i])) {
                return pairs[i + 1];
            }
            if (value.equals(pairs[i + 1])) {
                return pairs[i];
            }
        }
        return value;
    }

    /**
     * Rotation of signs, banners and skulls: 16 steps of 22.5°, 0 facing south, 4 west, 8 north, 12 east.
     */
    @Nullable
    private static String rotation(String value, Transform transform) {
        if (!transform.keepsVertical()) {
            return IMPOSSIBLE;
        }
        int rotation;
        try {
            rotation = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
        double angle = rotation * Math.PI * 2 / ROTATIONS;
        // Large enough to keep the 22.5° steps once rounded
        int[] v = transform.apply((int) Math.round(-Math.sin(angle) * 1000), 0, (int) Math.round(Math.cos(angle) * 1000));
        double turned = Math.atan2(-v[0], v[2]);
        return String.valueOf(Math.floorMod((int) Math.round(turned * ROTATIONS / (Math.PI * 2)), ROTATIONS));
    }

    /**
     * Orientation of crafters and jigsaws: front_top, like north_up.
     */
    @Nullable
    private static String orientation(String value, Transform transform) {
        int split = value.indexOf('_');
        if (split < 0) {
            return null;
        }
        String front = transform.direction(value.substring(0, split));
        String top = transform.direction(value.substring(split + 1));
        return front == null || top == null ? null : front + "_" + top;
    }

    /**
     * Shape of rails (north_south, ascending_east, south_west...). Stairs are turned by {@link #stairs}.
     */
    @Nullable
    private static String shape(String value, Transform transform, boolean flipY) {
        if (value.startsWith("ascending_")) {
            String direction = transform.direction(value.substring("ascending_".length()));
            if (direction == null) {
                return null;
            }
            if (!isHorizontal(direction)) {
                return IMPOSSIBLE;
            }
            // Upside down, the rail climbs the other way
            return "ascending_" + (flipY ? opposite(direction) : direction);
        }
        int split = value.indexOf('_');
        if (split < 0) {
            return null;
        }
        String a = transform.direction(value.substring(0, split));
        String b = transform.direction(value.substring(split + 1));
        if (a == null || b == null) {
            return null;
        }
        if (!isHorizontal(a) || !isHorizontal(b)) {
            return IMPOSSIBLE;
        }
        Set<String> ends = Set.of(a, b);
        if (ends.equals(Set.of("north", "south"))) {
            return "north_south";
        }
        if (ends.equals(Set.of("east", "west"))) {
            return "east_west";
        }
        // Corners are written north or south first
        String northSouth = ends.contains("north") ? "north" : "south";
        String eastWest = ends.contains("east") ? "east" : "west";
        return northSouth + "_" + eastWest;
    }

    private static boolean isHorizontal(String direction) {
        return !direction.equals("up") && !direction.equals("down");
    }

    private static String opposite(String direction) {
        return swap(direction, "north", "south", "east", "west", "up", "down");
    }
}

package fr.darkvodou.facets.symmetry;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the properties of a block state (facing=east, shape=outer_left...) the way a transform turns the block.
 * Works on the names and values as written in Minecraft, so it does not depend on a block registry.
 * A value the block cannot take (stairs facing up after a 3D turn) is left as it was.
 */
public final class BlockStateMirror {

    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");
    private static final int ROTATIONS = 16;

    private BlockStateMirror() {
    }

    /**
     * @param state   the property values of the block
     * @param allowed the values each property can take
     * @return the new property values, with the same properties
     */
    public static Map<String, String> transform(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                                Transform transform) {
        Map<String, String> result = new LinkedHashMap<>(state);
        if (transform.isIdentity()) {
            return result;
        }
        boolean flipY = transform.flipsVertical();
        boolean swapHands = transform.swapsHandedness();

        // Connections to each side (fences, walls, panes, vines, mushroom blocks, redstone...)
        for (String face : FACES) {
            if (!state.containsKey(face)) {
                continue;
            }
            String from = transform.inverse().direction(face);
            if (from != null && state.containsKey(from)) {
                set(result, allowed, face, state.get(from));
            }
        }

        for (Map.Entry<String, String> entry : state.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            String transformed = switch (name) {
                // Direction values: facing, vertical_direction (pointed dripstone)
                case "facing", "vertical_direction" -> transform.direction(value);
                case "axis" -> transform.axis(value);
                case "rotation" -> rotation(value, transform);
                case "orientation" -> orientation(value, transform);
                case "half" -> flipY ? swap(value, "top", "bottom", "upper", "lower") : value;
                // Slabs (top / bottom) and chests (left / right)
                case "type" -> swap(swap(value, flipY, "top", "bottom"), swapHands, "left", "right");
                case "shape" -> shape(value, transform, flipY, swapHands);
                case "hinge" -> swap(value, swapHands, "left", "right");
                // Buttons, levers, grindstones (face) and bells (attachment)
                case "face", "attachment" -> swap(value, flipY, "floor", "ceiling");
                // Lanterns, mangrove propagules
                case "hanging" -> swap(value, flipY, "true", "false");
                default -> value;
            };
            if (transformed != null && !transformed.equals(value)) {
                set(result, allowed, name, transformed);
            }
        }
        return result;
    }

    private static void set(Map<String, String> result, Map<String, ? extends Collection<String>> allowed, String name,
                            String value) {
        Collection<String> values = allowed.get(name);
        if (values == null || values.contains(value)) {
            result.put(name, value);
        }
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
            return null;
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
     * Shape of stairs (straight, inner_left, outer_right...) and rails (north_south, ascending_east, south_west...).
     */
    @Nullable
    private static String shape(String value, Transform transform, boolean flipY, boolean swapHands) {
        // Stairs: left and right are seen from above, so only a mirror seen from above changes them.
        // Upside down stairs keep their shape: it only depends on how they stand next to each other.
        if (value.endsWith("_left") || value.endsWith("_right")) {
            return swapHands ? swap(value, "inner_left", "inner_right", "outer_left", "outer_right") : value;
        }
        if (value.startsWith("ascending_")) {
            String direction = transform.direction(value.substring("ascending_".length()));
            if (direction == null || !isHorizontal(direction)) {
                return null;
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
        if (a == null || b == null || !isHorizontal(a) || !isHorizontal(b)) {
            return null;
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

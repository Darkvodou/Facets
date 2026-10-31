package fr.darkvodou.facets.replace;

import fr.darkvodou.facets.variants.CreativeOrder;
import fr.darkvodou.facets.variants.Variants;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Changes the type in a block name and keeps the rest: oak_stairs becomes spruce_stairs or diorite_stairs.
 * A type is a family of /family, except for wood, split in four types: spruce_planks (planks, stairs, slab, door...),
 * spruce_log (logs and wood), stripped_spruce_log (stripped logs and wood) and spruce_leaves (leaves and sapling).
 */
final class TypeReplace {

    // Shapes of full blocks, "" for the blocks named after their type alone (cobblestone, stone_bricks)
    private static final Set<String> FULL = Set.of("", "planks", "log", "wood", "stem", "hyphae", "block", "pillar",
            "wool", "concrete", "concrete_powder", "terracotta", "glazed_terracotta", "stained_glass");
    // The types a wood family is split in, by shape; the other shapes are planks
    private static final Map<String, String> WOOD_TYPES = Map.of(
            "log", "log", "wood", "log", "stem", "stem", "hyphae", "stem",
            "leaves", "leaves", "sapling", "leaves", "nylium", "fungus", "fungus", "fungus", "roots", "fungus");
    private static final List<String> WOOD_SUFFIXES = List.of("planks", "log", "stem", "leaves", "fungus");
    private static final String STRIPPED = "stripped_";

    private static volatile Set<String> woods;
    private static volatile Map<String, List<Material>> types;

    private TypeReplace() {
    }

    /**
     * @return the families with planks (oak, crimson, bamboo...), split in several types
     */
    static Set<String> woods() {
        Set<String> known = woods;
        if (known == null) {
            known = Arrays.stream(Material.values())
                    .filter(Variants::holdable)
                    .map(material -> Variants.key(material.name()))
                    .filter(key -> key.shape().equals("planks"))
                    .map(Variants.Key::family)
                    .collect(Collectors.toUnmodifiableSet());
            woods = known;
        }
        return known;
    }

    /**
     * @return the types of at least two blocks, with their blocks, in the order of the creative inventory
     */
    static Map<String, List<Material>> types(Logger logger) {
        Map<String, List<Material>> known = types;
        if (known == null) {
            Comparator<Material> order = CreativeOrder.comparator(logger);
            Set<String> woods = woods();
            known = Arrays.stream(Material.values())
                    .filter(Variants::holdable)
                    .sorted(order)
                    .collect(Collectors.groupingBy(material -> type(material.name().toLowerCase(Locale.ROOT), woods),
                            LinkedHashMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                            LinkedHashMap::new));
            types = known;
        }
        return known;
    }

    /**
     * @param woods the families split in several types, from {@link #woods()}
     * @return the type of the block: spruce_planks for spruce_stairs, spruce_log for spruce_wood,
     * stripped_spruce_log for stripped_spruce_wood, diorite for polished_diorite_slab
     */
    static String type(String name, Set<String> woods) {
        Variants.Key key = Variants.key(name);
        if (!woods.contains(key.family())) {
            return key.family();
        }
        String type = WOOD_TYPES.getOrDefault(key.shape(), "planks");
        boolean stripped = (type.equals("log") || type.equals("stem")) && name.startsWith(STRIPPED);
        return (stripped ? STRIPPED : "") + key.family() + "_" + type;
    }

    /**
     * @return the family of a type, the part of the block names it replaces: spruce for spruce_planks and
     * stripped_spruce_log
     */
    static String family(String type, Set<String> woods) {
        if (type.startsWith(STRIPPED)) {
            String family = family(type.substring(STRIPPED.length()), woods);
            if (woods.contains(family)) {
                return family;
            }
        }
        for (String suffix : WOOD_SUFFIXES) {
            String family = type.substring(0, Math.max(0, type.length() - suffix.length() - 1));
            if (type.endsWith("_" + suffix) && woods.contains(family)) {
                return family;
            }
        }
        return type;
    }

    /**
     * @param base the full block of the new type ({@link #base}), or null if it has none
     * @return the names to try for the block, best first: the same block in the new type (oak_stairs:
     * spruce_stairs), then for a full block the full block of the new type (spruce_log: cobblestone);
     * empty if it is not of that type
     */
    static List<String> retype(String name, String from, String to, @Nullable String base, Set<String> woods) {
        if (!type(name, woods).equals(from)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        String renamed = rename(name, family(from, woods), family(to, woods));
        // spruce_log to spruce_planks or stripped_spruce_log gives spruce_log again, cobblestone_stairs to spruce_log
        // gives spruce_stairs
        if (renamed != null && type(renamed, woods).equals(to)) {
            names.add(renamed);
        }
        if (base != null && full(name)) {
            names.add(base);
        }
        return names;
    }

    /**
     * @param blocks the blocks of the type, from {@link #types}
     * @return the full block standing for the type (cobblestone, spruce_planks, quartz_block), or null if it has none
     */
    @Nullable
    static String base(String type, List<Material> blocks) {
        List<String> full = blocks.stream()
                .map(block -> block.name().toLowerCase(Locale.ROOT))
                .filter(TypeReplace::full)
                .toList();
        for (String name : List.of(type, type + "s", type + "_planks", type + "_block")) {
            if (full.contains(name)) {
                return name;
            }
        }
        return full.isEmpty() ? null : full.getFirst();
    }

    /**
     * @return whether the block is a full block: logs, wood, planks, bricks, wool... but not stairs or leaves
     */
    static boolean full(String name) {
        return FULL.contains(Variants.key(name).shape());
    }

    /**
     * @return the name with the family replaced (oak_stairs: spruce_stairs), or null if it is not of that family
     */
    @Nullable
    static String rename(String name, String from, String to) {
        if (!Variants.key(name).family().equals(from)) {
            return null;
        }
        boolean pluralFrom = plural(from);
        for (int i = name.indexOf(from); i >= 0; i = name.indexOf(from, i + 1)) {
            int end = i + from.length();
            if (i > 0 && name.charAt(i - 1) != '_') {
                continue;
            }
            // stone_bricks for the stone_brick family
            if (pluralFrom && name.startsWith("s", end) && (end + 1 == name.length() || name.charAt(end + 1) == '_')) {
                return name.substring(0, i) + to + (plural(to) ? "s" : "") + name.substring(end + 1);
            }
            if (end == name.length() || name.charAt(end) == '_') {
                // diorite becomes stone_bricks, not stone_brick
                String suffix = end == name.length() && plural(to) ? "s" : "";
                return name.substring(0, i) + to + suffix + name.substring(end);
            }
        }
        return null;
    }

    private static boolean plural(String family) {
        return family.endsWith("brick") || family.endsWith("tile");
    }
}

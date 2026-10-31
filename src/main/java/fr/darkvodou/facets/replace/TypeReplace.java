package fr.darkvodou.facets.replace;

import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Changes the type in a block name and keeps the rest: oak_stairs becomes spruce_stairs or diorite_stairs.
 * A type is a full block (spruce_planks, polished_granite, stone_bricks, white_wool...) with its stairs, slab, wall,
 * fence, door... named after it. The waxed copper keeps its wax when the new type has a waxed version.
 */
final class TypeReplace {

    // The types, by their full block, in the order of the menu
    static final List<String> TYPES = List.of(
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "mangrove_planks", "bamboo_planks", "bamboo_mosaic", "cherry_planks", "pale_oak_planks",
            "stone", "smooth_stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite",
            "polished_andesite", "cobblestone", "mossy_cobblestone", "mossy_stone_bricks", "sandstone",
            "cut_sandstone", "smooth_sandstone", "bricks", "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone",
            "stone_bricks", "nether_bricks", "red_nether_bricks", "quartz_block", "smooth_quartz", "purpur_block",
            "prismarine", "prismarine_bricks", "dark_prismarine", "end_stone_bricks", "quartz_bricks", "blackstone",
            "polished_blackstone", "polished_blackstone_bricks", "crimson_planks", "warped_planks",
            "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles",
            "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper", "mud_bricks",
            "resin_bricks", "tuff", "tuff_bricks", "polished_tuff",
            "white_wool", "light_gray_wool", "gray_wool", "black_wool", "brown_wool", "red_wool", "orange_wool",
            "yellow_wool", "lime_wool", "green_wool", "cyan_wool", "light_blue_wool", "blue_wool", "purple_wool",
            "magenta_wool", "pink_wool");
    // The other blocks of a type, named after its stem: oak_stairs, stone_brick_wall
    private static final Set<String> SHAPES = Set.of("stairs", "slab", "wall", "fence", "fence_gate", "door",
            "trapdoor", "button", "pressure_plate", "sign", "hanging_sign", "shelf");
    private static final List<String> STEM_SUFFIXES = List.of("_planks", "_block");
    private static final String WAXED = "waxed_";

    private static volatile Map<String, List<Material>> types;

    private TypeReplace() {
    }

    /**
     * @return the types of this server's version, with their blocks (the full block first), in the order of the menu
     */
    static Map<String, List<Material>> types() {
        Map<String, List<Material>> known = types;
        if (known == null) {
            known = new LinkedHashMap<>();
            for (String type : TYPES) {
                Material full = Material.matchMaterial(type);
                if (full == null || !full.isBlock()) {
                    continue;
                }
                List<Material> blocks = new ArrayList<>(List.of(full));
                Arrays.stream(Material.values())
                        .filter(material -> material != full && material.isBlock() && !material.isLegacy())
                        .filter(material -> shape(material.name().toLowerCase(Locale.ROOT), type) != null)
                        .forEach(blocks::add);
                known.put(type, List.copyOf(blocks));
            }
            known = Collections.unmodifiableMap(known);
            types = known;
        }
        return known;
    }

    /**
     * @return the part of the names shared by the blocks of the type: oak for oak_planks, stone_brick for
     * stone_bricks, quartz for quartz_block
     */
    static String stem(String type) {
        for (String suffix : STEM_SUFFIXES) {
            if (type.endsWith(suffix)) {
                return type.substring(0, type.length() - suffix.length());
            }
        }
        if (type.endsWith("bricks") || type.endsWith("tiles")) {
            return type.substring(0, type.length() - 1);
        }
        return type;
    }

    /**
     * @param name a block name, without the waxed_ prefix
     * @return the shape of the block in the type ("" for the full block, stairs for oak_stairs in oak_planks), or
     * null if it is not of that type
     */
    @Nullable
    static String shape(String name, String type) {
        if (name.equals(type)) {
            return "";
        }
        String stem = stem(type) + "_";
        if (name.startsWith(stem) && SHAPES.contains(name.substring(stem.length()))) {
            return name.substring(stem.length());
        }
        return null;
    }

    /**
     * @return the names to try for the block, best first: the same block in the new type (oak_stairs: spruce_stairs),
     * waxed first for a waxed block; empty if it is not of that type. The blocks with no version in the new type
     * (oak_door in stone) are left as they are.
     */
    static List<String> retype(String name, String from, String to) {
        boolean waxed = name.startsWith(WAXED);
        String shape = shape(waxed ? name.substring(WAXED.length()) : name, from);
        if (shape == null) {
            return List.of();
        }
        String renamed = shape.isEmpty() ? to : stem(to) + "_" + shape;
        return waxed ? List.of(WAXED + renamed, renamed) : List.of(renamed);
    }
}

package fr.darkvodou.facets.variants;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Groups the blocks by their name: oak_stairs is the stairs of the oak family, cracked_stone_bricks a stone brick.
 */
public final class Variants {

    /**
     * The family of a block (oak, stone_brick, white...) and its shape (stairs, slab, wool...), empty for a full block.
     */
    record Key(String family, String shape) {
    }

    // Longest first, so _stained_glass_pane is not taken for _pane
    private static final List<String> SHAPES = Arrays.stream(new String[]{
                    "stairs", "slab", "wall", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate",
                    "sign", "hanging_sign", "shelf", "planks", "log", "wood", "stem", "hyphae", "leaves", "sapling",
                    "nylium", "fungus", "roots", "block", "pillar", "grate", "bulb", "pane", "carpet",
                    "wool", "concrete", "concrete_powder", "terracotta", "glazed_terracotta", "stained_glass",
                    "stained_glass_pane", "bed", "banner", "candle", "shulker_box"})
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    // Words that make another variant of the same family: cracked_stone_bricks is a stone brick
    private static final Set<String> MODIFIERS = Set.of(
            "stripped", "chiseled", "cracked", "mossy", "polished", "smooth", "cut", "cobbled", "waxed", "mosaic");

    private Variants() {
    }

    static Key key(String name) {
        String family = name.toLowerCase(Locale.ROOT);
        String shape = "";
        // white_wool_stairs: stairs of the white family
        for (boolean stripped = true; stripped; ) {
            stripped = false;
            for (String candidate : SHAPES) {
                if (family.endsWith("_" + candidate)) {
                    shape = shape.isEmpty() ? candidate : shape;
                    family = family.substring(0, family.length() - candidate.length() - 1);
                    stripped = true;
                    break;
                }
            }
        }
        // stone_bricks and stone_brick_stairs
        if (family.endsWith("bricks") || family.endsWith("tiles")) {
            family = family.substring(0, family.length() - 1);
        }
        List<String> words = new ArrayList<>(List.of(family.split("_")));
        if (words.removeIf(MODIFIERS::contains) && !words.isEmpty()) {
            family = String.join("_", words);
        }
        return new Key(family, shape);
    }

    /**
     * Blocks a player can hold: no legacy, wall-mounted or infested blocks.
     */
    static boolean holdable(Material material) {
        return material.isBlock() && material.isItem() && !material.isAir() && !material.isLegacy()
                && !material.name().startsWith("INFESTED_");
    }

    /**
     * @return the blocks of the same family as the material (oak_stairs: every oak block), itself included
     */
    public static List<Material> sameFamily(Material material) {
        String family = key(material.name()).family();
        return Arrays.stream(Material.values())
                .filter(Variants::holdable)
                .filter(other -> key(other.name()).family().equals(family))
                .toList();
    }

    /**
     * @return the blocks of the same shape (oak_stairs: every stairs), or only the material if it is a full block
     */
    public static List<Material> sameShape(Material material) {
        String shape = key(material.name()).shape();
        if (shape.isEmpty()) {
            return List.of(material);
        }
        return Arrays.stream(Material.values())
                .filter(Variants::holdable)
                .filter(other -> key(other.name()).shape().equals(shape))
                .toList();
    }
}

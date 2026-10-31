package fr.darkvodou.facets.replace;

import org.bukkit.DyeColor;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Changes the color in a block name and keeps the rest: red_wool_stairs becomes lime_wool_stairs.
 */
final class ColorReplace {

    /**
     * The 16 dye colors, as they are written in block names.
     */
    static final List<String> COLORS = Arrays.stream(DyeColor.values())
            .map(color -> color.name().toLowerCase(Locale.ROOT))
            .toList();

    private ColorReplace() {
    }

    /**
     * @return the name with the color replaced (red_bed: lime_bed), or null if it has no word of that color
     */
    @Nullable
    static String recolor(String name, String from, String to) {
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            // light_blue and light_gray are one color, not blue and gray
            if (i + 1 < words.length && COLORS.contains(word + "_" + words[i + 1])) {
                word += "_" + words[++i];
            }
            if (word.equals(from)) {
                word = to;
                found = true;
            }
            if (!result.isEmpty()) {
                result.append('_');
            }
            result.append(word);
        }
        return found ? result.toString() : null;
    }
}

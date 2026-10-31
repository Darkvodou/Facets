package fr.darkvodou.facets.replace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorReplaceTest {

    @Test
    void replacesTheColorWord() {
        assertEquals("lime_wool", ColorReplace.recolor("red_wool", "red", "lime"));
        assertEquals("lime_shulker_box", ColorReplace.recolor("red_shulker_box", "red", "lime"));
        assertEquals("lime_wool_stairs", ColorReplace.recolor("red_wool_stairs", "red", "lime"));
        assertEquals("potted_lime_tulip", ColorReplace.recolor("potted_red_tulip", "red", "lime"));
    }

    @Test
    void lightColorsAreOneColor() {
        assertNull(ColorReplace.recolor("light_gray_wool", "gray", "red"));
        assertNull(ColorReplace.recolor("light_blue_bed", "blue", "red"));
        assertEquals("red_bed", ColorReplace.recolor("light_blue_bed", "light_blue", "red"));
        assertEquals("light_gray_concrete", ColorReplace.recolor("gray_concrete", "gray", "light_gray"));
    }

    @Test
    void otherWordsAreKept() {
        assertNull(ColorReplace.recolor("redstone_block", "red", "lime"));
        assertNull(ColorReplace.recolor("blue_ice", "red", "lime"));
        assertNull(ColorReplace.recolor("stone", "red", "lime"));
    }
}

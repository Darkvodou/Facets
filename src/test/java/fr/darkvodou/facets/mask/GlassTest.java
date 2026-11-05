package fr.darkvodou.facets.mask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassTest {

    @Test
    void glassOfTheTagIsKept() {
        assertTrue(Glass.isGlass("glass"));
        assertTrue(Glass.isGlass("red_stained_glass"));
        assertTrue(Glass.isGlass("tinted_glass"));
    }

    @Test
    void otherBlocksOfTheTagAreLeftOut() {
        assertFalse(Glass.isGlass("barrier"));
    }
}

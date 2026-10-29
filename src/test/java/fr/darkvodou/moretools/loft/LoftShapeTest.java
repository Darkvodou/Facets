package fr.darkvodou.moretools.loft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoftShapeTest {

    @Test
    void findsShapesByTheirSelectionName() {
        assertEquals(LoftShape.SMOOTH, LoftShape.fromSelection("loft"));
        assertEquals(LoftShape.POLY, LoftShape.fromSelection("loftpoly"));
        assertEquals(LoftShape.POLY, LoftShape.fromSelection("loftp"));
        assertEquals(LoftShape.FACET, LoftShape.fromSelection("LoftFacet"));
        assertEquals(LoftShape.FACET, LoftShape.fromSelection("loftf"));
        assertNull(LoftShape.fromSelection("cuboid"));
    }

    @Test
    void findsShapesByTheirSubcommand() {
        assertEquals(LoftShape.SMOOTH, LoftShape.fromSubcommand("smooth"));
        assertEquals(LoftShape.POLY, LoftShape.fromSubcommand("poly"));
        assertEquals(LoftShape.FACET, LoftShape.fromSubcommand("facet"));
        assertNull(LoftShape.fromSubcommand("set"));
    }

    @Test
    void messagesHaveAVariantPerShape() {
        assertEquals("loft.selected", LoftShape.SMOOTH.message("loft.selected"));
        assertEquals("loft.selected-poly", LoftShape.POLY.message("loft.selected"));
        assertEquals("loft.selected-facet", LoftShape.FACET.message("loft.selected"));
    }

    @Test
    void onlySmoothUsesSplines() {
        assertFalse(LoftShape.SMOOTH.straight());
        assertTrue(LoftShape.POLY.straight());
        assertTrue(LoftShape.FACET.straight());
    }
}

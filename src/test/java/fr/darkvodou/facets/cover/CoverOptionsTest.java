package fr.darkvodou.facets.cover;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverOptionsTest {

    @Test
    void groupedFlagsExcludeTheirFaces() {
        CoverOptions options = CoverOptions.parse(new String[]{"8", "-es"}, 0);
        assertEquals("8", options.radius());
        assertFalse(options.random());
        assertEquals(EnumSet.of(Cover.Face.WEST, Cover.Face.NORTH), options.excluded());
    }

    @Test
    void randomGoesWithTheDirections() {
        CoverOptions options = CoverOptions.parse(new String[]{"fixcover", "-RUd", "5"}, 1);
        assertEquals("5", options.radius());
        assertTrue(options.random());
        assertEquals(EnumSet.of(Cover.Face.UP, Cover.Face.DOWN), options.excluded());
        assertTrue(CoverOptions.parse(new String[]{"-random"}, 0).random());
    }

    @Test
    void flagsExcludeTheBlocksFacingThatWay() {
        assertEquals(EnumSet.of(Cover.Face.DOWN), CoverOptions.parse(new String[]{"-u"}, 0).excluded());
        assertEquals(EnumSet.of(Cover.Face.UP), CoverOptions.parse(new String[]{"-d"}, 0).excluded());
    }

    @Test
    void longNamesAreAliases() {
        CoverOptions options = CoverOptions.parse(new String[]{"-East", "4", "-down", "-random"}, 0);
        assertEquals("4", options.radius());
        assertTrue(options.random());
        assertEquals(EnumSet.of(Cover.Face.WEST, Cover.Face.UP), options.excluded());
        assertEquals(List.of("-north"), CoverOptions.complete(new String[]{"-no"}, 0, List.of()));
        assertEquals(List.of(), CoverOptions.complete(new String[]{"-n", "-no"}, 0, List.of()));
        assertEquals(List.of("-n", "-s", "-w", "-u"),
                CoverOptions.complete(new String[]{"-east", "-rd", "-"}, 0, List.of()));
    }

    @Test
    void unknownFlagsAndExtraValuesAreInvalid() {
        assertNull(CoverOptions.parse(new String[]{"-x"}, 0));
        assertNull(CoverOptions.parse(new String[]{"3", "4"}, 0));
    }

    @Test
    void describesTheBrushOptions() {
        assertEquals(", random, without -n -w",
                CoverOptions.parse(new String[]{"-wnr"}, 0).describe());
        assertEquals(", without -d -u", CoverOptions.parse(new String[]{"-ud"}, 0).describe());
        assertEquals("", CoverOptions.parse(new String[]{"3"}, 0).describe());
    }

    @Test
    void suggestsTheFlagsNotUsedYet() {
        assertEquals(List.of("-r", "-n", "-w", "-u", "-d"),
                CoverOptions.complete(new String[]{"fixcover", "5", "-es", "-"}, 1, List.of("5")));
        assertEquals(List.of("3", "-r", "-n", "-s", "-e", "-w", "-u", "-d"),
                CoverOptions.complete(new String[]{"fixcover", ""}, 1, List.of("3")));
    }
}

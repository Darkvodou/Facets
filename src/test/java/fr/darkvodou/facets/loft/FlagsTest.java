package fr.darkvodou.facets.loft;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagsTest {

    private final Flags flags = new Flags('o', "outline", 'n', "net", 'u', "uniform");

    @Test
    void parsesShortLongAndCombinedFlags() {
        assertEquals("n", flags.parse("-n"));
        assertEquals("n", flags.parse("-net"));
        assertEquals("n", flags.parse("-NET"));
        assertEquals("nu", flags.parse("-nu"));
        assertEquals("u", flags.parse("-uniform"));
    }

    @Test
    void keepsUnknownLettersToReportThem() {
        assertEquals("x", flags.parse("-x"));
        assertFalse(flags.isKnown('x'));
        assertTrue(flags.isKnown('n'));
    }

    @Test
    void numbersAndWordsAreNotFlags() {
        assertNull(flags.parse("-5"));
        assertNull(flags.parse("stone"));
        assertNull(flags.parse("-"));
    }

    @Test
    void completesBothFormsOfEveryFlag() {
        assertEquals(List.of("-o", "-outline", "-n", "-net", "-u", "-uniform"), flags.all(Set.of()));
    }

    @Test
    void completesCombinationsAfterTheTypedFlag() {
        assertEquals(List.of("-n", "-net", "-no", "-nu"), flags.complete("-n", Set.of()));
        assertEquals(List.of("-net"), flags.complete("-ne", Set.of()));
    }

    @Test
    void doesNotOfferUsedFlags() {
        assertEquals(List.of("-o", "-outline", "-u", "-uniform"), flags.all(Set.of('n')));
    }

    @Test
    void completesNothingForAValue() {
        assertTrue(flags.complete("stone", Set.of()).isEmpty());
    }
}

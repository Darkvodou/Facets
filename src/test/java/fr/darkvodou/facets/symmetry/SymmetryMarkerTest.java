package fr.darkvodou.facets.symmetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryMarkerTest {

    @Test
    void everyTypeHasAMarker() {
        for (SymmetryType type : SymmetryType.values()) {
            assertTrue(SymmetryMarker.points(type) > 0, type.id() + " has no marker look");
        }
    }
}

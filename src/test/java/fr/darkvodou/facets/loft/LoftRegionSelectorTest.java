package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoftRegionSelectorTest {

    private static LoftRegionSelector selector() {
        LoftRegionSelector selector = new LoftRegionSelector(null, LoftShape.SMOOTH);
        selector.selectPrimary(BlockVector3.at(0, 64, 0), null);
        selector.selectSecondary(BlockVector3.at(10, 70, 0), null);
        selector.selectSecondary(BlockVector3.at(20, 64, 0), null);
        selector.selectPrimary(BlockVector3.at(0, 64, 15), null);
        selector.selectSecondary(BlockVector3.at(20, 64, 15), null);
        return selector;
    }

    @Test
    void pos1StartsFramesAndPos2AddsPoints() {
        LoftRegionSelector selector = selector();
        assertEquals(2, selector.getLoft().getFrameCount());
        assertEquals(5, selector.getLoft().getPointCount());
        assertEquals(5, selector.getVertices().size());
        assertTrue(selector.isDefined());
    }

    @Test
    void repeatedClicksAreIgnored() {
        LoftRegionSelector selector = new LoftRegionSelector(null, LoftShape.SMOOTH);
        assertTrue(selector.selectPrimary(BlockVector3.at(0, 0, 0), null));
        assertFalse(selector.selectPrimary(BlockVector3.at(0, 0, 0), null));
        assertTrue(selector.selectSecondary(BlockVector3.at(1, 0, 0), null));
        assertFalse(selector.selectSecondary(BlockVector3.at(1, 0, 0), null));
        assertEquals(2, selector.getLoft().getPointCount());
    }

    @Test
    void incompleteSelectionHasNoRegion() {
        LoftRegionSelector selector = new LoftRegionSelector(null, LoftShape.SMOOTH);
        assertThrows(IncompleteRegionException.class, selector::getRegion);
        assertThrows(IncompleteRegionException.class, selector::getPrimaryPosition);
        assertEquals(0, selector.getVolume());
    }

    @Test
    void switchingShapeKeepsTheFrames() {
        LoftRegionSelector smooth = selector();
        for (LoftShape shape : LoftShape.values()) {
            LoftRegionSelector switched = LoftRegionSelector.from(null, shape, smooth);
            assertEquals(shape, switched.getLoft().getShape());
            assertEquals(shape.typeName(), switched.getTypeName());
            assertEquals(smooth.getLoft().getFrames(), switched.getLoft().getFrames());
            assertNotSame(smooth.getLoft(), switched.getLoft());
        }
    }

    @Test
    void switchedSelectionIsIndependent() {
        LoftRegionSelector smooth = selector();
        LoftRegionSelector poly = LoftRegionSelector.from(null, LoftShape.POLY, smooth);
        poly.selectSecondary(BlockVector3.at(25, 64, 15), null);
        assertEquals(5, smooth.getLoft().getPointCount());
        assertEquals(6, poly.getLoft().getPointCount());
    }

    @Test
    void otherSelectionsStartEmpty() {
        LoftRegionSelector loft = LoftRegionSelector.from(null, LoftShape.FACET, new CuboidRegionSelector());
        assertEquals(0, loft.getLoft().getPointCount());
    }

    @Test
    void clearRemovesEverything() {
        LoftRegionSelector selector = selector();
        selector.clear();
        assertEquals(0, selector.getLoft().getPointCount());
        assertFalse(selector.isDefined());
    }
}

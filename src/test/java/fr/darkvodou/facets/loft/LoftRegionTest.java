package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.darkvodou.facets.loft.LoftTesting.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoftRegionTest {

    /**
     * Two frames of 6 blocks, 5 blocks apart: a flat 6x6 sheet.
     */
    private static LoftRegion sheet() {
        LoftRegion region = new LoftRegion(null, LoftShape.SMOOTH);
        region.addFrame(BlockVector3.at(0, 64, 0));
        region.addPoint(BlockVector3.at(5, 64, 0));
        region.addFrame(BlockVector3.at(0, 64, 5));
        region.addPoint(BlockVector3.at(5, 64, 5));
        return region;
    }

    @Test
    void holdsTheFilledSurface() {
        LoftRegion region = sheet();
        assertEquals(36, region.getVolume());
        assertEquals(BlockVector3.at(0, 64, 0), region.getMinimumPoint());
        assertEquals(BlockVector3.at(5, 64, 5), region.getMaximumPoint());
        assertTrue(region.contains(BlockVector3.at(2, 64, 3)));
        assertFalse(region.contains(BlockVector3.at(2, 65, 3)));
    }

    @Test
    void iteratesOverItsBlocks() {
        LoftRegion region = sheet();
        Set<BlockVector3> blocks = new HashSet<>();
        region.forEach(blocks::add);
        assertEquals(region.getVolume(), blocks.size());
        assertTrue(blocks.stream().allMatch(region::contains));
    }

    @Test
    void needsTwoPoints() {
        LoftRegion region = new LoftRegion(null, LoftShape.SMOOTH);
        assertFalse(region.isDefined());
        region.addFrame(BlockVector3.at(0, 0, 0));
        assertFalse(region.isDefined());
        region.addFrame(BlockVector3.at(5, 0, 0));
        assertTrue(region.isDefined());
    }

    @Test
    void pos2WithoutFrameStartsOne() {
        LoftRegion region = new LoftRegion(null, LoftShape.SMOOTH);
        region.addPoint(BlockVector3.at(1, 2, 3));
        assertEquals(List.of(frame(1, 2, 3)), region.getFrames());
    }

    @Test
    void removesTheLastFrameAndPoint() {
        LoftRegion region = sheet();
        assertEquals(BlockVector3.at(5, 64, 5), region.removeLastPoint());
        // The last frame has no pos2 left: the previous one gives its point
        assertEquals(BlockVector3.at(5, 64, 0), region.removeLastPoint());
        assertNull(region.removeLastPoint());
        assertEquals(BlockVector3.at(0, 64, 5), region.removeLastFrame());
        assertEquals(1, region.getFrameCount());
    }

    @Test
    void removesTheNearestFrameAndPoint() {
        LoftRegion region = sheet();
        assertEquals(BlockVector3.at(5, 64, 0), region.removeNearestPoint(Vector3.at(6, 64, -1)));
        assertEquals(BlockVector3.at(0, 64, 5), region.removeNearestFrame(Vector3.at(0, 64, 9)));
        assertEquals(List.of(frame(0, 64, 0)), region.getFrames());
    }

    @Test
    void changesBumpTheVersion() {
        LoftRegion region = sheet();
        int version = region.getVersion();
        region.addPoint(BlockVector3.at(9, 64, 5));
        assertTrue(region.getVersion() > version);
    }

    @Test
    void shiftMovesTheFrames() {
        LoftRegion region = sheet();
        region.shift(BlockVector3.at(1, 2, 3));
        assertEquals(BlockVector3.at(1, 66, 3), region.getMinimumPoint());
        assertEquals(36, region.getVolume());
    }

    @Test
    void cloneIsIndependent() {
        LoftRegion region = sheet();
        LoftRegion clone = region.clone();
        clone.addPoint(BlockVector3.at(9, 64, 5));
        assertEquals(4, region.getPointCount());
        assertEquals(5, clone.getPointCount());
        assertEquals(region.getShape(), clone.getShape());
    }

    @Test
    void shapeChangesTheBlocks() {
        List<List<BlockVector3>> frames = LoftTesting.lineThenCorners();
        LoftRegion poly = new LoftRegion(null, LoftShape.POLY, frames);
        LoftRegion facet = new LoftRegion(null, LoftShape.FACET, frames);
        Set<BlockVector3> polyBlocks = new HashSet<>();
        poly.forEach(polyBlocks::add);
        Set<BlockVector3> facetBlocks = new HashSet<>();
        facet.forEach(facetBlocks::add);
        assertNotEquals(polyBlocks, facetBlocks);
    }
}

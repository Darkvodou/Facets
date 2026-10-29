package fr.darkvodou.moretools.loft;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static fr.darkvodou.moretools.loft.LoftTesting.curvedSheet;
import static fr.darkvodou.moretools.loft.LoftTesting.frame;
import static fr.darkvodou.moretools.loft.LoftTesting.missingColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoftSurfaceTest {

    private static LoftOptions lines(LoftShape shape, boolean closed, boolean outline, int count, boolean horizontal,
                                     boolean net, boolean uniform) {
        return new LoftOptions(shape, closed, outline, count, horizontal, net, uniform, false);
    }

    private static Set<BlockVector3> blocks(List<List<BlockVector3>> frames, LoftOptions options) {
        return new LoftSurface(frames, options).blocks(options);
    }

    @ParameterizedTest
    @EnumSource(LoftShape.class)
    void fillHasNoHoles(LoftShape shape) {
        Set<BlockVector3> fill = blocks(curvedSheet(), LoftOptions.fill(shape));
        assertEquals(0, missingColumns(fill, 20));
    }

    @Test
    void fillGoesThroughTheControlPoints() {
        Set<BlockVector3> fill = blocks(curvedSheet(), LoftOptions.FILL);
        for (List<BlockVector3> frame : curvedSheet()) {
            for (BlockVector3 point : frame) {
                assertTrue(fill.contains(point), point.toString());
            }
        }
    }

    @Test
    void singleFrameIsACurve() {
        Set<BlockVector3> curve = blocks(List.of(frame(0, 64, 0, 10, 64, 0)), LoftOptions.FILL);
        assertEquals(11, curve.size());
    }

    /**
     * The lines must stay inside the fill: //replace uses the fill, so it must find every block set with lines.
     */
    @Test
    void linesAreAlwaysInsideTheFill() {
        Random random = new Random(42);
        int cases = 0;
        for (int c = 0; c < 20; c++) {
            List<List<BlockVector3>> frames = LoftTesting.randomFrames(random);
            for (LoftShape shape : LoftShape.values()) {
                for (boolean closed : new boolean[]{false, true}) {
                    Set<BlockVector3> fill = blocks(frames, new LoftOptions(shape, closed, false, 0, false, false, false, false));
                    for (int count : new int[]{1, 4}) {
                        for (boolean horizontal : new boolean[]{false, true}) {
                            for (boolean net : new boolean[]{false, true}) {
                                for (boolean uniform : new boolean[]{false, true}) {
                                    for (boolean outline : new boolean[]{false, true}) {
                                        LoftOptions options = lines(shape, closed, outline, count, horizontal, net, uniform);
                                        Set<BlockVector3> lines = blocks(frames, options);
                                        assertTrue(fill.containsAll(lines), frames + " " + options);
                                        cases++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(20 * 3 * 2 * 2 * 2 * 2 * 2 * 2, cases);
    }

    @Test
    void uniformLinesAreEvenlySpaced() {
        // Frames 2 then 18 blocks apart
        List<List<BlockVector3>> frames = List.of(
                frame(0, 64, 0, 10, 64, 0),
                frame(0, 64, 2, 10, 64, 2),
                frame(0, 64, 20, 10, 64, 20));
        assertEquals(List.of(0, 1, 2, 10, 20), lineRows(frames, false));
        assertEquals(List.of(0, 5, 10, 15, 20), lineRows(frames, true));
    }

    private static List<Integer> lineRows(List<List<BlockVector3>> frames, boolean uniform) {
        Set<BlockVector3> lines = blocks(frames, lines(LoftShape.SMOOTH, false, false, 5, true, false, uniform));
        return new ArrayList<>(new TreeSet<>(lines.stream().map(BlockVector3::z).toList()));
    }

    @Test
    void netHasBothDirections() {
        Set<BlockVector3> rails = blocks(curvedSheet(), lines(LoftShape.SMOOTH, false, false, 5, false, false, false));
        Set<BlockVector3> crosses = blocks(curvedSheet(), lines(LoftShape.SMOOTH, false, false, 5, true, false, false));
        Set<BlockVector3> net = blocks(curvedSheet(), lines(LoftShape.SMOOTH, false, false, 5, false, true, false));
        Set<BlockVector3> both = new java.util.HashSet<>(rails);
        both.addAll(crosses);
        assertEquals(both, net);
    }

    @Test
    void closedPolyLoftKeepsFlatWalls() {
        // Vertical frames at the corners of a square
        List<List<BlockVector3>> frames = List.of(
                frame(0, 64, 0, 0, 74, 0),
                frame(10, 64, 0, 10, 74, 0),
                frame(10, 64, 10, 10, 74, 10),
                frame(0, 64, 10, 0, 74, 10));
        for (LoftShape shape : new LoftShape[]{LoftShape.POLY, LoftShape.FACET}) {
            Set<BlockVector3> walls = blocks(frames, new LoftOptions(shape, true, false, 0, false, false, false, false));
            assertTrue(walls.stream().allMatch(p -> p.x() == 0 || p.x() == 10 || p.z() == 0 || p.z() == 10), shape.name());
        }
        Set<BlockVector3> tube = blocks(frames, new LoftOptions(LoftShape.SMOOTH, true, false, 0, false, false, false, false));
        assertTrue(tube.stream().anyMatch(p -> p.x() != 0 && p.x() != 10 && p.z() != 0 && p.z() != 10));
    }

    @Test
    void shapesDifferWhenFramesDiffer() {
        List<List<BlockVector3>> frames = LoftTesting.lineThenCorners();
        Set<BlockVector3> poly = blocks(frames, LoftOptions.fill(LoftShape.POLY));
        Set<BlockVector3> facet = blocks(frames, LoftOptions.fill(LoftShape.FACET));
        assertNotEquals(poly, facet);
    }
}

package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplineTest {

    private static final double EPSILON = 1e-9;

    private static final List<Vector3> POINTS = List.of(
            Vector3.at(0, 0, 0), Vector3.at(10, 5, 0), Vector3.at(20, 0, 3), Vector3.at(30, 8, 3));

    @Test
    void passesThroughItsControlPoints() {
        for (boolean linear : new boolean[]{false, true}) {
            Spline spline = new Spline(POINTS, false, linear);
            for (int i = 0; i < POINTS.size(); i++) {
                Vector3 point = spline.at((double) i / spline.segments());
                assertEquals(0, point.distance(POINTS.get(i)), EPSILON, "linear=" + linear + ", point " + i);
            }
        }
    }

    @Test
    void linearSplineIsAPolyline() {
        Spline spline = new Spline(POINTS, false, true);
        Vector3 middle = spline.at(0.5 / spline.segments());
        assertEquals(0, middle.distance(Vector3.at(5, 2.5, 0)), EPSILON);
    }

    @Test
    void closedSplineHasOneMoreSegmentAndLoops() {
        Spline open = new Spline(POINTS, false, false);
        Spline closed = new Spline(POINTS, true, false);
        assertEquals(open.segments() + 1, closed.segments());
        assertEquals(0, closed.at(1).distance(POINTS.getFirst()), EPSILON);
    }

    @Test
    void twoPointsCannotBeClosed() {
        assertEquals(1, new Spline(POINTS.subList(0, 2), true, false).segments());
    }

    @Test
    void singlePointIsConstant() {
        Spline spline = new Spline(List.of(Vector3.at(1, 2, 3)), false, false);
        assertEquals(0, spline.segments());
        assertEquals(0, spline.length());
        assertEquals(Vector3.at(1, 2, 3), spline.at(0.7));
        assertEquals(Vector3.at(1, 2, 3), spline.atLength(0.7));
    }

    @Test
    void lengthOfAPolylineIsExact() {
        Spline spline = new Spline(List.of(Vector3.at(0, 0, 0), Vector3.at(3, 4, 0), Vector3.at(3, 4, 10)), false, true);
        assertEquals(15, spline.length(), 1e-6);
    }

    @Test
    void atLengthIsEvenlySpaced() {
        // Segments of very different lengths: by parameter the points would bunch up on the short one
        Spline spline = new Spline(List.of(Vector3.at(0, 0, 0), Vector3.at(1, 0, 0), Vector3.at(21, 0, 0)), false, true);
        for (int k = 0; k <= 10; k++) {
            assertEquals(2.1 * k, spline.atLength(k / 10.0).x(), 1e-3);
        }
    }

    @Test
    void samplesAreCloseEnough() {
        Spline spline = new Spline(POINTS, false, false);
        List<Vector3> samples = new ArrayList<>();
        spline.sample(0.3, samples::add);
        for (int i = 1; i < samples.size(); i++) {
            assertTrue(samples.get(i - 1).distance(samples.get(i)) <= 0.35, "gap at sample " + i);
        }
        assertEquals(0, samples.getFirst().distance(POINTS.getFirst()), EPSILON);
        assertEquals(0, samples.getLast().distance(POINTS.getLast()), EPSILON);
    }
}

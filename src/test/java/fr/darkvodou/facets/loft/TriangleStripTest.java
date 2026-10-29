package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangleStripTest {

    private static final List<Vector3> LINE = List.of(Vector3.at(0, 0, 0), Vector3.at(20, 0, 0));
    private static final List<Vector3> CORNERS = List.of(
            Vector3.at(0, 10, 15), Vector3.at(5, 18, 12), Vector3.at(12, 9, 20), Vector3.at(20, 16, 14));

    private final TriangleStrip strip = new TriangleStrip(
            TriangleStrip.Vertices.of(LINE), TriangleStrip.Vertices.of(CORNERS));

    @Test
    void edgesAreTheFrames() {
        Spline line = new Spline(LINE, false, true);
        Spline corners = new Spline(CORNERS, false, true);
        for (int i = 0; i <= 100; i++) {
            double u = i / 100.0;
            assertEquals(0, strip.at(u, 0).distance(line.atLength(u)), 1e-6);
            assertEquals(0, strip.at(u, 1).distance(corners.atLength(u)), 1e-6);
        }
    }

    @Test
    void trianglesAreFlat() {
        // An affine (flat) mapping has no twist: its mixed second difference is zero.
        // It can only be non zero on the edges between two triangles.
        assertTrue(twistedShare(strip::at) < 0.02);
    }

    @Test
    void straightRailsBetweenDifferentFramesAreTwisted() {
        // What the loft poly shape does: each u joined by a straight segment
        Spline line = new Spline(LINE, false, true);
        Spline corners = new Spline(CORNERS, false, true);
        BiFunction<Double, Double, Vector3> ruled = (u, t) -> {
            Vector3 a = line.atLength(u);
            return a.add(corners.atLength(u).subtract(a).multiply(t));
        };
        assertTrue(twistedShare(ruled::apply) > 0.9);
    }

    @Test
    void singlePointFrameMakesAFan() {
        TriangleStrip fan = new TriangleStrip(
                TriangleStrip.Vertices.of(List.of(Vector3.at(10, 10, 10))), TriangleStrip.Vertices.of(CORNERS));
        for (int i = 0; i <= 10; i++) {
            assertEquals(0, fan.at(i / 10.0, 0).distance(Vector3.at(10, 10, 10)), 1e-9);
        }
    }

    private static double twistedShare(BiFunction<Double, Double, Vector3> surface) {
        double h = 1e-3;
        int twisted = 0;
        int samples = 0;
        for (int i = 1; i < 100; i++) {
            for (int j = 1; j < 100; j++) {
                double u = i / 100.0;
                double t = j / 100.0;
                Vector3 mixed = surface.apply(u + h, t + h).subtract(surface.apply(u + h, t - h))
                        .subtract(surface.apply(u - h, t + h)).add(surface.apply(u - h, t - h));
                samples++;
                if (mixed.length() > 1e-6) {
                    twisted++;
                }
            }
        }
        return (double) twisted / samples;
    }
}

package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.Vector3;

import java.util.List;

/**
 * Curve passing through its control points: centripetal Catmull-Rom (no cusps or loops),
 * or a plain polyline in low poly mode.
 */
final class Spline extends Curve {

    private final Vector3[] points;
    private final boolean closed;
    private final boolean linear;

    Spline(List<Vector3> points, boolean closed, boolean linear) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("A spline needs at least one point");
        }
        this.points = points.toArray(new Vector3[0]);
        this.closed = closed && points.size() > 2;
        this.linear = linear;
    }

    @Override
    int segments() {
        int n = points.length;
        if (n <= 1) {
            return 0;
        }
        return closed ? n : n - 1;
    }

    @Override
    Vector3 single() {
        return points[0];
    }

    @Override
    Vector3 segment(int i, double f) {
        Vector3 p1 = point(i);
        Vector3 p2 = point(i + 1);
        if (linear) {
            return p1.add(p2.subtract(p1).multiply(f));
        }
        int n = points.length;
        Vector3 p0 = closed || i > 0 ? point(i - 1) : p1.multiply(2).subtract(p2);
        Vector3 p3 = closed || i + 2 < n ? point(i + 2) : p2.multiply(2).subtract(p1);
        return catmullRom(p0, p1, p2, p3, f);
    }

    private Vector3 point(int i) {
        return points[Math.floorMod(i, points.length)];
    }

    // Barry-Goldman evaluation of a centripetal Catmull-Rom segment between p1 and p2
    private static Vector3 catmullRom(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, double f) {
        double t0 = 0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);
        double t = t1 + (t2 - t1) * f;
        Vector3 a1 = mix(p0, p1, t0, t1, t);
        Vector3 a2 = mix(p1, p2, t1, t2, t);
        Vector3 a3 = mix(p2, p3, t2, t3, t);
        Vector3 b1 = mix(a1, a2, t0, t2, t);
        Vector3 b2 = mix(a2, a3, t1, t3, t);
        return mix(b1, b2, t1, t2, t);
    }

    private static double knot(Vector3 a, Vector3 b) {
        return Math.max(Math.sqrt(a.distance(b)), 1e-3);
    }

    private static Vector3 mix(Vector3 a, Vector3 b, double ta, double tb, double t) {
        double w = (t - ta) / (tb - ta);
        return a.multiply(1 - w).add(b.multiply(w));
    }
}

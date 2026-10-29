package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Band between two low poly frames, cut into flat triangles whose corners are the vertices of the frames.
 * It is parameterized like the rest of the loft: u is a fraction of each frame's length,
 * t goes from 0 on the first frame to 1 on the second one.
 */
final class TriangleStrip {

    /**
     * Vertices of a low poly frame, with their position as a fraction of the frame length.
     */
    record Vertices(double[] fractions, Vector3[] points) {

        static Vertices of(List<Vector3> points) {
            double[] lengths = new double[points.size()];
            for (int i = 1; i < points.size(); i++) {
                lengths[i] = lengths[i - 1] + points.get(i - 1).distance(points.get(i));
            }
            double total = lengths[lengths.length - 1];
            if (total < 1e-9) {
                // A single point: the edge of the band shrinks to it
                Vector3 point = points.getFirst();
                return new Vertices(new double[]{0, 1}, new Vector3[]{point, point});
            }
            double[] fractions = new double[lengths.length];
            for (int i = 0; i < lengths.length; i++) {
                fractions[i] = lengths[i] / total;
            }
            return new Vertices(fractions, points.toArray(new Vector3[0]));
        }

        int last() {
            return points.length - 1;
        }
    }

    // Edges between the triangles, from left to right: edge k joins vertex a[k] of the first frame
    // to vertex b[k] of the second one. Two edges in a row bound a triangle.
    private final double[] ua;
    private final double[] ub;
    private final Vector3[] pa;
    private final Vector3[] pb;

    TriangleStrip(Vertices a, Vertices b) {
        // Walk both frames, always moving to the closest next vertex, so that triangles stay thin
        List<int[]> edges = new ArrayList<>();
        int i = 0;
        int j = 0;
        edges.add(new int[]{0, 0});
        while (i < a.last() || j < b.last()) {
            if (i == a.last()) {
                j++;
            } else if (j == b.last()) {
                i++;
            } else if (a.fractions()[i + 1] <= b.fractions()[j + 1]) {
                i++;
            } else {
                j++;
            }
            edges.add(new int[]{i, j});
        }
        ua = new double[edges.size()];
        ub = new double[edges.size()];
        pa = new Vector3[edges.size()];
        pb = new Vector3[edges.size()];
        for (int k = 0; k < edges.size(); k++) {
            int[] edge = edges.get(k);
            ua[k] = a.fractions()[edge[0]];
            ub[k] = b.fractions()[edge[1]];
            pa[k] = a.points()[edge[0]];
            pb[k] = b.points()[edge[1]];
        }
    }

    /**
     * Point of the band. Inside a triangle the mapping is affine, so the triangle stays flat.
     */
    Vector3 at(double u, double t) {
        u = Math.clamp(u, 0, 1);
        t = Math.clamp(t, 0, 1);
        // Edges never cross, so their positions at height t are sorted
        int lo = 0;
        int hi = ua.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (x(mid, t) <= u) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double left = x(lo, t);
        double width = x(hi, t) - left;
        double s = width <= 1e-12 ? 0 : Math.clamp((u - left) / width, 0, 1);
        Vector3 l = lerp(pa[lo], pb[lo], t);
        Vector3 r = lerp(pa[hi], pb[hi], t);
        return lerp(l, r, s);
    }

    private double x(int k, double t) {
        return ua[k] + (ub[k] - ua[k]) * t;
    }

    private static Vector3 lerp(Vector3 a, Vector3 b, double f) {
        return a.add(b.subtract(a).multiply(f));
    }
}

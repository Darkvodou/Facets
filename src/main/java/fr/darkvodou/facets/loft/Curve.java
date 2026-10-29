package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.math.Vector3;

import java.util.function.Consumer;

/**
 * Curve made of segments, each evaluated on [0, 1]. Takes care of the arc length and of the sampling.
 */
abstract class Curve {

    // Arc length table, built on demand
    private double[] tableT;
    private double[] tableLength;

    abstract int segments();

    /**
     * Point at {@code f} in [0, 1] of segment {@code i}.
     */
    abstract Vector3 segment(int i, double f);

    /**
     * The point of a curve without segments.
     */
    abstract Vector3 single();

    /**
     * Point at {@code t} in [0, 1], segments being spread uniformly.
     */
    Vector3 at(double t) {
        int segments = segments();
        if (segments == 0) {
            return single();
        }
        double s = Math.clamp(t, 0, 1) * segments;
        int i = Math.min((int) s, segments - 1);
        return segment(i, s - i);
    }

    /**
     * Point at {@code u} in [0, 1] of the curve length.
     */
    Vector3 atLength(double u) {
        return at(tAtLength(u));
    }

    /**
     * Parameter t (as taken by {@link #at}) at {@code u} in [0, 1] of the curve length.
     */
    double tAtLength(double u) {
        if (segments() == 0) {
            return 0;
        }
        buildTable();
        double target = Math.clamp(u, 0, 1) * tableLength[tableLength.length - 1];
        int lo = 0;
        int hi = tableLength.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (tableLength[mid] < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double span = tableLength[hi] - tableLength[lo];
        double f = span <= 1e-9 ? 0 : (target - tableLength[lo]) / span;
        return tableT[lo] + (tableT[hi] - tableT[lo]) * f;
    }

    double length() {
        if (segments() == 0) {
            return 0;
        }
        buildTable();
        return tableLength[tableLength.length - 1];
    }

    /**
     * Feeds points along the curve, at most {@code step} blocks apart.
     */
    void sample(double step, Consumer<Vector3> sink) {
        int segments = segments();
        if (segments == 0) {
            sink.accept(single());
            return;
        }
        for (int i = 0; i < segments; i++) {
            int count = Math.max(1, (int) Math.ceil(segmentLength(i) / step));
            for (int j = 0; j < count; j++) {
                sink.accept(segment(i, (double) j / count));
            }
        }
        sink.accept(segment(segments - 1, 1));
    }

    double segmentLength(int i) {
        double length = 0;
        Vector3 previous = segment(i, 0);
        for (int j = 1; j <= 8; j++) {
            Vector3 current = segment(i, j / 8.0);
            length += previous.distance(current);
            previous = current;
        }
        return length;
    }

    private void buildTable() {
        if (tableT != null) {
            return;
        }
        int segments = segments();
        int[] perSegment = new int[segments];
        int total = 1;
        for (int i = 0; i < segments; i++) {
            perSegment[i] = Math.max(8, (int) Math.ceil(segmentLength(i) * 4));
            total += perSegment[i];
        }
        double[] ts = new double[total];
        double[] lengths = new double[total];
        Vector3 previous = segment(0, 0);
        int k = 1;
        for (int i = 0; i < segments; i++) {
            for (int j = 1; j <= perSegment[i]; j++, k++) {
                double f = (double) j / perSegment[i];
                Vector3 current = segment(i, f);
                ts[k] = (i + f) / segments;
                lengths[k] = lengths[k - 1] + previous.distance(current);
                previous = current;
            }
        }
        tableT = ts;
        tableLength = lengths;
    }
}

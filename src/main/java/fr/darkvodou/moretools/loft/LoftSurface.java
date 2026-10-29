package fr.darkvodou.moretools.loft;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Surface swept through the frames: each frame is a spline (u along it, by length),
 * and a "rail" spline links the frames at a given u (v across them).
 * In poly and facet shapes, frames are polylines; in facet, the bands between them are cut into flat triangles.
 */
final class LoftSurface {

    /** Distance between samples when voxelizing: below half a block so the surface has no holes. */
    private static final double BLOCK_STEP = 0.3;

    private final List<Spline> frames = new ArrayList<>();
    private final boolean closed;
    private final boolean lowPoly;
    private final double maxFrameLength;
    // Facet only: the band after each frame
    private final List<TriangleStrip> strips = new ArrayList<>();

    LoftSurface(List<List<BlockVector3>> frames, LoftShape shape, boolean closed) {
        boolean lowPoly = shape.straight();
        double maxLength = 0;
        for (List<BlockVector3> frame : frames) {
            List<Vector3> centers = frame.stream().map(p -> p.toVector3().add(0.5, 0.5, 0.5)).toList();
            Spline spline = new Spline(centers, false, lowPoly);
            this.frames.add(spline);
            maxLength = Math.max(maxLength, spline.length());
        }
        this.lowPoly = lowPoly;
        this.closed = closed && frames.size() > 2;
        this.maxFrameLength = maxLength;
        if (shape == LoftShape.FACET && frames.size() > 1) {
            List<TriangleStrip.Vertices> vertices = frames.stream()
                    .map(frame -> TriangleStrip.Vertices.of(frame.stream().map(p -> p.toVector3().add(0.5, 0.5, 0.5)).toList()))
                    .toList();
            int count = this.closed ? vertices.size() : vertices.size() - 1;
            for (int i = 0; i < count; i++) {
                strips.add(new TriangleStrip(vertices.get(i), vertices.get((i + 1) % vertices.size())));
            }
        }
    }

    LoftSurface(List<List<BlockVector3>> frames, LoftOptions options) {
        this(frames, options.shape(), options.closed());
    }

    /**
     * Curve crossing every frame at {@code u}: a spline, a polyline in poly, or in facet
     * a polyline following the triangles.
     */
    Curve rail(double u) {
        if (!strips.isEmpty()) {
            return new PolyRail(u);
        }
        List<Vector3> points = new ArrayList<>(frames.size());
        for (Spline frame : frames) {
            points.add(frame.atLength(u));
        }
        return new Spline(points, closed, lowPoly);
    }

    /**
     * Curve following the frames at {@code v} (v = 0 is the first frame, v = 1 the last one).
     */
    void sampleCross(double v, double step, Consumer<Vector3> sink) {
        int count = Math.max(1, (int) Math.ceil(maxFrameLength / step));
        for (int i = 0; i <= count; i++) {
            sink.accept(rail((double) i / count).at(v));
        }
    }

    void sampleOutline(double step, Consumer<Vector3> sink) {
        rail(0).sample(step, sink);
        rail(1).sample(step, sink);
        if (!closed) {
            sampleCross(0, step, sink);
            sampleCross(1, step, sink);
        }
    }

    void sampleFrames(double step, Consumer<Vector3> sink) {
        for (Spline frame : frames) {
            frame.sample(step, sink);
        }
    }

    double maxFrameLength() {
        return maxFrameLength;
    }

    int frameSegments() {
        return closed ? frames.size() : frames.size() - 1;
    }

    /**
     * Blocks of the surface, or of its lines. Everything is sampled on one shared (u, v) grid,
     * so the lines are always inside the filled surface (the region used by //set, //replace...).
     */
    Set<BlockVector3> blocks(LoftOptions options) {
        Grid grid = new Grid(BLOCK_STEP);
        Set<BlockVector3> blocks = new HashSet<>();
        if (!options.outline() && options.count() <= 0) {
            for (int i = 0; i < grid.us.length; i++) {
                grid.addRail(i, blocks);
            }
            return blocks;
        }
        if (options.outline()) {
            grid.addRail(0, blocks);
            grid.addRail(grid.us.length - 1, blocks);
            if (!closed) {
                grid.addCross(0, blocks);
                grid.addCross(grid.vs.length - 1, blocks);
            }
        }
        int count = options.count();
        if (count > 0) {
            boolean rails = options.net() || !options.horizontal();
            boolean crosses = options.net() || options.horizontal();
            if (rails) {
                for (int k = 0; k < count; k++) {
                    grid.addRail(grid.nearestU(spread(k, count)), blocks);
                }
            }
            if (crosses) {
                for (int k = 0; k < count; k++) {
                    // On a closed loft v = 1 is v = 0 again
                    double v = closed ? (double) k / count : spread(k, count);
                    if (options.uniform()) {
                        grid.addUniformCross(v, blocks);
                    } else {
                        grid.addCross(grid.nearestV(v), blocks);
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Samples of the surface: rails at each u of {@code us}, each evaluated at every v of {@code vs}.
     * Both are dense enough for neighbor samples to be at most {@code step} apart.
     */
    private final class Grid {

        final double[] us;
        final double[] vs;
        private final Curve[] rails;

        Grid(double step) {
            vs = vGrid(step);
            us = uGrid(step);
            rails = new Curve[us.length];
            for (int i = 0; i < us.length; i++) {
                rails[i] = rail(us[i]);
            }
        }

        // Each segment between two frames gets as many samples as its longest rail needs
        private double[] vGrid(double step) {
            Curve middle = rail(0.5);
            int segments = middle.segments();
            if (segments == 0) {
                return new double[]{0};
            }
            Curve[] probes = new Curve[9];
            for (int k = 0; k < probes.length; k++) {
                probes[k] = rail(k / 8.0);
            }
            List<Double> values = new ArrayList<>();
            for (int s = 0; s < segments; s++) {
                double longest = 0;
                for (Curve probe : probes) {
                    longest = Math.max(longest, probe.segmentLength(s));
                }
                int count = Math.max(1, (int) Math.ceil(longest / step));
                for (int j = 0; j < count; j++) {
                    values.add((s + (double) j / count) / segments);
                }
            }
            values.add(1.0);
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        }

        // As many rails as the longest curve following the frames needs
        private double[] uGrid(double step) {
            int probes = 64;
            Curve[] rails = new Curve[probes + 1];
            for (int k = 0; k <= probes; k++) {
                rails[k] = rail((double) k / probes);
            }
            int vProbes = Math.min(vs.length, 65);
            double longest = maxFrameLength;
            for (int j = 0; j < vProbes; j++) {
                double v = vs[(int) ((long) j * (vs.length - 1) / Math.max(1, vProbes - 1))];
                double length = 0;
                Vector3 previous = rails[0].at(v);
                for (int k = 1; k <= probes; k++) {
                    Vector3 current = rails[k].at(v);
                    length += previous.distance(current);
                    previous = current;
                }
                longest = Math.max(longest, length);
            }
            int count = Math.max(1, (int) Math.ceil(longest / step));
            double[] values = new double[count + 1];
            for (int i = 0; i <= count; i++) {
                values[i] = (double) i / count;
            }
            return values;
        }

        int nearestU(double u) {
            return (int) Math.round(Math.clamp(u, 0, 1) * (us.length - 1));
        }

        int nearestV(double v) {
            return nearest(vs, v);
        }

        void addRail(int i, Set<BlockVector3> blocks) {
            for (double v : vs) {
                add(rails[i].at(v), blocks);
            }
        }

        void addCross(int j, Set<BlockVector3> blocks) {
            for (Curve rail : rails) {
                add(rail.at(vs[j]), blocks);
            }
        }

        /**
         * Curve at {@code w} of the length of every rail, so that such curves are evenly spaced.
         */
        void addUniformCross(double w, Set<BlockVector3> blocks) {
            for (Curve rail : rails) {
                add(rail.at(vs[nearest(vs, rail.tAtLength(w))]), blocks);
            }
        }

        private static int nearest(double[] values, double value) {
            int index = java.util.Arrays.binarySearch(values, value);
            if (index >= 0) {
                return index;
            }
            int above = -index - 1;
            if (above == 0) {
                return 0;
            }
            if (above == values.length) {
                return values.length - 1;
            }
            return value - values[above - 1] <= values[above] - value ? above - 1 : above;
        }

        private static void add(Vector3 p, Set<BlockVector3> blocks) {
            blocks.add(BlockVector3.at(Math.floor(p.x()), Math.floor(p.y()), Math.floor(p.z())));
        }
    }

    private static double spread(int k, int count) {
        return count == 1 ? 0.5 : (double) k / (count - 1);
    }

    /**
     * Facet rail: across each band, the straight line at {@code u} of its triangles.
     */
    private final class PolyRail extends Curve {

        private final double u;

        PolyRail(double u) {
            this.u = u;
        }

        @Override
        int segments() {
            return strips.size();
        }

        @Override
        Vector3 segment(int i, double f) {
            return strips.get(i).at(u, f);
        }

        @Override
        Vector3 single() {
            return frames.getFirst().atLength(u);
        }
    }
}

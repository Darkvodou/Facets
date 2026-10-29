package fr.darkvodou.moretools.loft;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.AbstractRegion;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.world.World;
import fr.darkvodou.moretools.Messages;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Surface lofted through frames. Each frame is a spline: its first point comes from //pos1,
 * the next ones from //pos2. The region holds the blocks of the filled surface.
 * Its {@link LoftShape} says whether it uses splines, straight segments or flat triangles.
 */
public class LoftRegion extends AbstractRegion {

    private final List<List<BlockVector3>> frames = new ArrayList<>();
    private final LoftShape loftShape;
    private int version;
    @Nullable
    private volatile Shape shape;

    private record Shape(Set<BlockVector3> blocks, BlockVector3 min, BlockVector3 max) {
    }

    public LoftRegion(@Nullable World world, LoftShape shape) {
        super(world);
        this.loftShape = shape;
    }

    /**
     * @param frames frames to copy
     */
    public LoftRegion(@Nullable World world, LoftShape shape, List<List<BlockVector3>> frames) {
        this(world, shape);
        for (List<BlockVector3> frame : frames) {
            this.frames.add(new ArrayList<>(frame));
        }
    }

    public LoftShape getShape() {
        return loftShape;
    }

    /**
     * How the region itself is filled.
     */
    public LoftOptions fillOptions() {
        return LoftOptions.fill(loftShape);
    }

    // ---- Frames ----

    public synchronized void addFrame(BlockVector3 start) {
        List<BlockVector3> frame = new ArrayList<>();
        frame.add(start);
        frames.add(frame);
        changed();
    }

    /**
     * Adds a point to the last frame, or starts a frame if there is none.
     */
    public synchronized void addPoint(BlockVector3 point) {
        if (frames.isEmpty()) {
            addFrame(point);
            return;
        }
        frames.getLast().add(point);
        changed();
    }

    /**
     * Removes the last frame with all its points.
     *
     * @return the start of the removed frame, or null if there was none
     */
    @Nullable
    public synchronized BlockVector3 removeLastFrame() {
        if (frames.isEmpty()) {
            return null;
        }
        BlockVector3 start = frames.removeLast().getFirst();
        changed();
        return start;
    }

    /**
     * Removes the frame whose start is the closest to {@code from}.
     */
    @Nullable
    public synchronized BlockVector3 removeNearestFrame(Vector3 from) {
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < frames.size(); i++) {
            double distance = center(frames.get(i).getFirst()).distanceSq(from);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        if (nearest < 0) {
            return null;
        }
        BlockVector3 start = frames.remove(nearest).getFirst();
        changed();
        return start;
    }

    /**
     * Removes the last //pos2 point, searching from the last frame backwards.
     */
    @Nullable
    public synchronized BlockVector3 removeLastPoint() {
        for (int i = frames.size() - 1; i >= 0; i--) {
            List<BlockVector3> frame = frames.get(i);
            if (frame.size() > 1) {
                BlockVector3 point = frame.removeLast();
                changed();
                return point;
            }
        }
        return null;
    }

    /**
     * Removes the //pos2 point closest to {@code from}.
     */
    @Nullable
    public synchronized BlockVector3 removeNearestPoint(Vector3 from) {
        List<BlockVector3> nearestFrame = null;
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (List<BlockVector3> frame : frames) {
            for (int i = 1; i < frame.size(); i++) {
                double distance = center(frame.get(i)).distanceSq(from);
                if (distance < best) {
                    best = distance;
                    nearestFrame = frame;
                    nearest = i;
                }
            }
        }
        if (nearestFrame == null) {
            return null;
        }
        BlockVector3 point = nearestFrame.remove(nearest);
        changed();
        return point;
    }

    public synchronized List<List<BlockVector3>> getFrames() {
        return frames.stream().map(List::copyOf).toList();
    }

    public synchronized int getFrameCount() {
        return frames.size();
    }

    public synchronized int getPointCount() {
        return frames.stream().mapToInt(List::size).sum();
    }

    @Nullable
    public synchronized BlockVector3 getFirstPoint() {
        return frames.isEmpty() ? null : frames.getFirst().getFirst();
    }

    @Nullable
    public synchronized BlockVector3 getLastPoint() {
        return frames.isEmpty() ? null : frames.getLast().getLast();
    }

    /**
     * Incremented on every change, so viewers know when to redraw.
     */
    public synchronized int getVersion() {
        return version;
    }

    public synchronized void clear() {
        frames.clear();
        changed();
    }

    /**
     * Two points at least: a single frame gives a curve, single point frames a spline through them.
     */
    public boolean isDefined() {
        return getPointCount() >= 2;
    }

    private void changed() {
        version++;
        shape = null;
    }

    private static Vector3 center(BlockVector3 block) {
        return block.toVector3().add(0.5, 0.5, 0.5);
    }

    // ---- Shape ----

    private Shape shape() {
        Shape current = shape;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (shape == null) {
                shape = computeShape();
            }
            return shape;
        }
    }

    private Shape computeShape() {
        if (frames.isEmpty()) {
            return new Shape(Set.of(), BlockVector3.ZERO, BlockVector3.ZERO);
        }
        LoftOptions options = fillOptions();
        Set<BlockVector3> blocks = new LoftSurface(getFrames(), options).blocks(options);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockVector3 block : blocks) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }
        return new Shape(Collections.unmodifiableSet(blocks),
                BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
    }

    // ---- Region ----

    @Override
    public BlockVector3 getMinimumPoint() {
        return shape().min();
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return shape().max();
    }

    @Override
    public long getVolume() {
        return shape().blocks().size();
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return shape().blocks().contains(position);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return contains(BlockVector3.at(x, y, z));
    }

    @Override
    public boolean contains(int x, int z) {
        for (BlockVector3 block : shape().blocks()) {
            if (block.x() == x && block.z() == z) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        return shape().blocks().iterator();
    }

    @Override
    public Set<BlockVector2> getChunks() {
        Set<BlockVector2> chunks = new HashSet<>();
        for (BlockVector3 block : shape().blocks()) {
            chunks.add(BlockVector2.at(block.x() >> 4, block.z() >> 4));
        }
        return chunks;
    }

    @Override
    public Set<BlockVector3> getChunkCubes() {
        Set<BlockVector3> cubes = new HashSet<>();
        for (BlockVector3 block : shape().blocks()) {
            cubes.add(BlockVector3.at(block.x() >> 4, block.y() >> 4, block.z() >> 4));
        }
        return cubes;
    }

    @Override
    public synchronized void shift(BlockVector3 change) {
        for (List<BlockVector3> frame : frames) {
            frame.replaceAll(point -> point.add(change));
        }
        changed();
    }

    @Override
    public void expand(BlockVector3... changes) throws RegionOperationException {
        throw new RegionOperationException(Messages.worldEdit("loft.cannot-expand"));
    }

    @Override
    public void contract(BlockVector3... changes) throws RegionOperationException {
        throw new RegionOperationException(Messages.worldEdit("loft.cannot-contract"));
    }

    @Override
    public synchronized LoftRegion clone() {
        LoftRegion clone = new LoftRegion(world, loftShape, frames);
        clone.shape = shape;
        return clone;
    }
}

package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.limit.SelectorLimits;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.world.World;
import fr.darkvodou.facets.Messages;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Loft selection: //pos1 starts a new frame, //pos2 adds a point to the current frame.
 * Its shape can be smooth, poly (straight segments) or facet (flat triangles).
 */
public class LoftRegionSelector implements RegionSelector {

    private final transient LoftRegion region;

    public LoftRegionSelector(@Nullable World world, LoftShape shape) {
        region = new LoftRegion(world, shape);
    }

    /**
     * Selector of the given kind, keeping the frames of a previous loft selection if there is one.
     */
    public static LoftRegionSelector from(@Nullable World world, LoftShape shape, @Nullable RegionSelector previous) {
        if (previous instanceof LoftRegionSelector loft) {
            return new LoftRegionSelector(new LoftRegion(world, shape, loft.region.getFrames()));
        }
        return new LoftRegionSelector(world, shape);
    }

    private LoftRegionSelector(LoftRegion region) {
        this.region = region;
    }

    public LoftRegion getLoft() {
        return region;
    }

    @Nullable
    @Override
    public World getWorld() {
        return region.getWorld();
    }

    @Override
    public void setWorld(@Nullable World world) {
        region.setWorld(world);
    }

    @Override
    public boolean selectPrimary(BlockVector3 position, SelectorLimits limits) {
        // Ignore a repeated click on the start of a frame that has no other point yet
        List<List<BlockVector3>> frames = region.getFrames();
        if (!frames.isEmpty() && frames.getLast().size() == 1 && frames.getLast().getFirst().equals(position)) {
            return false;
        }
        region.addFrame(position);
        return true;
    }

    @Override
    public boolean selectSecondary(BlockVector3 position, SelectorLimits limits) {
        if (position.equals(region.getLastPoint())) {
            return false;
        }
        region.addPoint(position);
        return true;
    }

    @Override
    public void explainPrimarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        Messages.send(actor, "loft.primary", "frame", region.getFrameCount(), "position", position,
                "volume", region.getVolume());
    }

    @Override
    public void explainSecondarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        List<List<BlockVector3>> frames = region.getFrames();
        Messages.send(actor, "loft.secondary", "point", frames.getLast().size(), "frame", frames.size(),
                "position", position, "volume", region.getVolume());
    }

    @Override
    public void explainRegionAdjust(Actor actor, LocalSession session) {
    }

    @Override
    public BlockVector3 getPrimaryPosition() throws IncompleteRegionException {
        BlockVector3 first = region.getFirstPoint();
        if (first == null) {
            throw new IncompleteRegionException();
        }
        return first;
    }

    @Override
    public Region getRegion() throws IncompleteRegionException {
        if (!region.isDefined()) {
            throw new IncompleteRegionException();
        }
        return region;
    }

    @Override
    public Region getIncompleteRegion() {
        return region;
    }

    @Override
    public boolean isDefined() {
        return region.isDefined();
    }

    @Override
    public long getVolume() {
        return region.isDefined() ? region.getVolume() : 0;
    }

    @Override
    public void learnChanges() {
    }

    @Override
    public void clear() {
        region.clear();
    }

    @Override
    public String getTypeName() {
        return region.getShape().typeName();
    }

    @Override
    public List<Component> getSelectionInfoLines() {
        return List.of(
                Messages.worldEdit("loft.info-frames", "frames", region.getFrameCount()),
                Messages.worldEdit("loft.info-points", "points", region.getPointCount())
        );
    }

    @Override
    public List<BlockVector3> getVertices() {
        return region.getFrames().stream().flatMap(List::stream).toList();
    }
}

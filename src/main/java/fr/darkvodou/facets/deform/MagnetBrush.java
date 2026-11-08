package fr.darkvodou.facets.deform;

import com.fastasyncworldedit.core.util.MaskTraverser;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import fr.darkvodou.facets.Messages;

import javax.annotation.Nullable;

/**
 * The brush of //fdeform magnet, in two clicks: a left click chooses the zone (the sphere of the brush size around the
 * point clicked, the size being read then), then a right click pulls that zone toward the point clicked. The points
 * are the ones FAWE finds with the range and the trace mask (/range, /tracemask). FAWE handles the rest like for its
 * own brushes: /size, /mask, /smask, //undo...
 * <p>
 * The zone is kept between the clicks, so several right clicks pull the same zone toward different points.
 */
final class MagnetBrush implements Brush {

    /**
     * The zone chosen with the left click, shared by both clicks of a bound item.
     */
    static final class Zone {

        @Nullable
        private volatile BlockVector3 center;
        @Nullable
        private volatile String world;
        private volatile double radius;
    }

    /**
     * The left click: chooses the center of the zone.
     */
    static final class Select implements Brush {

        private final Zone zone;

        Select(Zone zone) {
            this.zone = zone;
        }

        @Override
        public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) {
            // The radius is the one of the left click: a /size before the right click does not change the zone
            zone.radius = size;
            zone.world = editSession.getWorld().id();
            zone.center = position.toImmutable();
            if (editSession.getActor() instanceof Actor actor) {
                Messages.send(actor, "fdeform.brush-zone", "x", position.x(), "y", position.y(), "z", position.z(),
                        "radius", size == Math.floor(size) ? String.valueOf((long) size) : String.valueOf(size));
            }
        }
    }

    private final Zone zone;
    private final int force;
    @Nullable
    private final Mask movable;
    private final boolean trim;

    MagnetBrush(Zone zone, int force, @Nullable Mask movable, boolean trim) {
        this.zone = zone;
        this.force = force;
        this.movable = movable;
        this.trim = trim;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size)
            throws MaxChangedBlocksException {
        Actor actor = editSession.getActor() instanceof Actor a ? a : null;
        BlockVector3 center = zone.center;
        if (center == null || !editSession.getWorld().id().equals(zone.world)) {
            if (actor != null) {
                Messages.send(actor, "fdeform.brush-no-zone");
            }
            return;
        }
        if (movable != null) {
            // The mask reads the blocks of the world the brush is used in
            new MaskTraverser(movable).reset(editSession);
        }
        double radius = zone.radius;
        EllipsoidRegion region = new EllipsoidRegion(editSession.getWorld(), center,
                Vector3.at(radius, radius, radius));
        try {
            MagnetDeform.apply(editSession, region, position.toVector3(), force, movable, trim);
        } catch (MaxChangedBlocksException e) {
            throw e;
        } catch (WorldEditException e) {
            if (actor != null) {
                actor.printError(e.getRichMessage());
            }
        }
    }
}

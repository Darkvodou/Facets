package fr.darkvodou.facets.cover;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.EllipsoidRegion;

import java.util.Random;

/**
 * The brush of //fixcover: fixes the covers of the sphere of the brush size around the point clicked. FAWE handles the
 * rest like for its own brushes: /size, /range, /mask (with the global mask, chooses which covers are fixed), //undo...
 */
final class FixCoverBrush implements Brush {

    private final boolean random;

    FixCoverBrush(boolean random) {
        this.random = random;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size)
            throws MaxChangedBlocksException {
        EllipsoidRegion region = new EllipsoidRegion(editSession.getWorld(), position.toImmutable(),
                Vector3.at(size, size, size));
        try {
            CoverFixer.fix(editSession, region, random ? new Random() : null);
        } catch (MaxChangedBlocksException e) {
            throw e;
        } catch (WorldEditException e) {
            if (editSession.getActor() instanceof Actor actor) {
                actor.printError(e.getRichMessage());
            }
        }
    }
}

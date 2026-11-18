package fr.darkvodou.facets.cover;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fixes the covers of a region (see {@link Cover}), for //fixcover and its brush.
 */
final class CoverFixer {

    private CoverFixer() {
    }

    /**
     * Fixes the covers of the region through the edit session, whose masks choose which of them are changed.
     *
     * @param random turns the blocks that could hold on several faces to one of them at random, or removes them if null
     */
    static void fix(EditSession editSession, Region region, @Nullable Random random) throws WorldEditException {
        int minY = editSession.getWorld().getMinY();
        int maxY = editSession.getWorld().getMaxY();
        // Every change is worked out before the first one is made, so they are all read from the same blocks
        List<Map.Entry<BlockVector3, BaseBlock>> changes = new ArrayList<>();
        for (BlockVector3 next : region) {
            // FAWE's cuboid regions give the same mutable vector for every block: kept, it must be copied
            BlockVector3 position = next.toImmutable();
            if (position.y() < minY || position.y() > maxY) {
                continue;
            }
            BlockState state = editSession.getBlock(position);
            if (!Cover.isCover(state)) {
                continue;
            }
            BlockState fixed = Cover.fix(state, position, editSession::getBlock, random);
            if (!fixed.equals(state)) {
                changes.add(Map.entry(position, withData(editSession.getFullBlock(position), fixed)));
            }
        }
        for (Map.Entry<BlockVector3, BaseBlock> change : changes) {
            editSession.setBlock(change.getKey(), change.getValue());
        }
    }

    /**
     * @return the fixed block with the data of the block it replaces (banner patterns, shelf items), unless removed
     */
    private static BaseBlock withData(BaseBlock block, BlockState fixed) {
        if (block.getNbtReference() == null || fixed.getBlockType().getMaterial().isAir()
                || fixed.getBlockType() == BlockTypes.WATER) {
            return fixed.toBaseBlock();
        }
        return fixed.toBaseBlock(block.getNbtReference());
    }
}

package fr.darkvodou.facets.deform;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls the blocks of a zone toward a point (see {@link Magnet}): what //fdeform magnet and its brush share.
 */
final class MagnetDeform {

    private static final BaseBlock AIR = BlockTypes.AIR.getDefaultState().toBaseBlock();

    private MagnetDeform() {
    }

    /**
     * @param zone     the blocks deformed
     * @param toward   the point the blocks are pulled toward, in block coordinates (a block at its center)
     * @param force    from 1 to 100
     * @param movable  like the source mask of //move -m: only these blocks are pulled, the others stay in place;
     *                 null for every block
     * @param trim     whether the deformation stays in the zone, else the blocks of the zone pulled out of it land
     *                 there too (without the air of the zone replacing what is around)
     */
    static void apply(EditSession editSession, Region zone, Vector3 toward, int force, @Nullable Mask movable,
            boolean trim) throws WorldEditException {
        int worldMinY = editSession.getMinY();
        int worldMaxY = editSession.getMaxY();
        BlockVector3 min = zone.getMinimumPoint();
        BlockVector3 max = zone.getMaximumPoint();
        int minY = Math.max(min.y(), worldMinY);
        int maxY = Math.min(max.y(), worldMaxY);
        if (minY > maxY) {
            return;
        }
        int sizeX = max.x() - min.x() + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = max.z() - min.z() + 1;
        double targetX = toward.x();
        double targetY = toward.y();
        double targetZ = toward.z();

        boolean[] inside = new boolean[sizeX * sizeY * sizeZ];
        // The blocks that show when pulled (not air, and in the mask): the full pull goes to the first one
        boolean[] solid = new boolean[inside.length];
        for (BlockVector3 position : zone) {
            if (position.y() >= minY && position.y() <= maxY) {
                int index = ((position.y() - minY) * sizeZ + position.z() - min.z()) * sizeX + position.x() - min.x();
                inside[index] = true;
                solid[index] = !editSession.getBlock(position).getBlockType().getMaterial().isAir()
                        && (movable == null || movable.test(position));
            }
        }
        Magnet magnet = new Magnet(min.x(), minY, min.z(), sizeX, sizeY, sizeZ, inside, solid,
                targetX, targetY, targetZ);

        // Every change is worked out before the first one is made, so they are all read from the same blocks
        List<BlockVector3> positions = new ArrayList<>();
        List<BaseBlock> blocks = new ArrayList<>();
        // Without the trim, the blocks pulled can land anywhere between the zone and the point
        int targetBlockX = (int) Math.floor(targetX + 0.5);
        int targetBlockY = (int) Math.floor(targetY + 0.5);
        int targetBlockZ = (int) Math.floor(targetZ + 0.5);
        int fromX = trim ? min.x() : Math.min(min.x(), targetBlockX);
        int fromY = trim ? minY : Math.max(worldMinY, Math.min(minY, targetBlockY));
        int fromZ = trim ? min.z() : Math.min(min.z(), targetBlockZ);
        int toX = trim ? max.x() : Math.max(max.x(), targetBlockX);
        int toY = trim ? maxY : Math.min(worldMaxY, Math.max(maxY, targetBlockY));
        int toZ = trim ? max.z() : Math.max(max.z(), targetBlockZ);
        for (int by = fromY; by <= toY; by++) {
            for (int bz = fromZ; bz <= toZ; bz++) {
                for (int bx = fromX; bx <= toX; bx++) {
                    boolean selected = inside(inside, bx - min.x(), by - minY, bz - min.z(), sizeX, sizeY, sizeZ);
                    if (trim && !selected) {
                        continue;
                    }
                    long source = magnet.source(bx, by, bz, force);
                    int sx = Magnet.x(source);
                    int sy = Magnet.y(source);
                    int sz = Magnet.z(source);
                    if (sx == bx && sy == by && sz == bz || sy < worldMinY || sy > worldMaxY) {
                        continue;
                    }
                    BlockVector3 position = BlockVector3.at(bx, by, bz);
                    BlockVector3 from = BlockVector3.at(sx, sy, sz);
                    BaseBlock block;
                    if (!selected) {
                        // Out of the zone, only the blocks of the zone land, and not its air
                        if (!inside(inside, sx - min.x(), sy - minY, sz - min.z(), sizeX, sizeY, sizeZ)
                                || movable != null && !movable.test(from)) {
                            continue;
                        }
                        block = editSession.getFullBlock(from);
                        if (block.getBlockType().getMaterial().isAir()) {
                            continue;
                        }
                    } else if (movable == null || movable.test(from)) {
                        block = editSession.getFullBlock(from);
                    } else if (movable.test(position)) {
                        // Like FAWE's //move -m: a block of the mask moving away leaves air behind it
                        block = AIR;
                    } else {
                        // Out of the mask and nothing from the mask comes here: it stays
                        continue;
                    }
                    if (!block.equals(editSession.getFullBlock(position))) {
                        positions.add(position);
                        blocks.add(block);
                    }
                }
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            editSession.setBlock(positions.get(i), blocks.get(i));
        }
    }

    /**
     * Whether a block of the bounding box of the zone (coordinates from its corner) is in the zone.
     */
    private static boolean inside(boolean[] inside, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ
                && inside[(y * sizeZ + z) * sizeX + x];
    }
}

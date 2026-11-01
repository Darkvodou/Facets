package fr.darkvodou.facets.aim;

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BaseBlock;
import fr.darkvodou.facets.Messages;

import java.util.function.IntSupplier;

/**
 * Finds the block a player is looking at, for {@code #faim}.
 */
final class AimedBlock {

    private AimedBlock() {
    }

    /**
     * @param maxRange how far the block can be (blocks), read on each use so a reload applies at once
     * @return the state of the aimed block, without its NBT
     * @throws InputParseException if the actor is not a player or looks at no block within range
     */
    static BaseBlock find(ParserContext context, IntSupplier maxRange) throws InputParseException {
        if (!(context.getActor() instanceof Player player)) {
            throw new InputParseException(Messages.worldEdit("aim.player-only"));
        }
        int range = Math.max(1, maxRange.getAsInt());
        Location target = player.getBlockTrace(range, false);
        if (target == null) {
            throw new InputParseException(Messages.worldEdit("aim.no-block", "range", range));
        }
        return player.getWorld().getBlock(target.toVector().toBlockPoint()).toBaseBlock();
    }
}

package fr.darkvodou.facets.hotbar;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.world.block.BlockType;
import fr.darkvodou.facets.Messages;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks in a player's hotbar, for {@code #fhotbar}.
 */
final class HotbarBlocks {

    static final int SLOTS = 9;

    private HotbarBlocks() {
    }

    /**
     * @return the type of each hotbar slot holding a block, from left to right: a block in two slots is there twice
     * @throws InputParseException if the actor is not a player or has no block in their hotbar
     */
    static List<BlockType> find(ParserContext context) throws InputParseException {
        if (!(context.getActor() instanceof Player player)) {
            throw new InputParseException(Messages.worldEdit("hotbar.player-only"));
        }
        PlayerInventory inventory = BukkitAdapter.adapt(player).getInventory();
        List<BlockType> types = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            // Only the items that are blocks: not air, not sticks or swords
            if (item == null || !item.getType().isBlock() || item.getType().isAir()) {
                continue;
            }
            BlockType type = BukkitAdapter.asBlockType(item.getType());
            if (type != null) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            throw new InputParseException(Messages.worldEdit("hotbar.no-block"));
        }
        return types;
    }
}

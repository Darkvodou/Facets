package fr.darkvodou.facets.replace;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import fr.darkvodou.facets.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.enginehub.linbus.tree.LinCompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Replaces the blocks of the selection by the blocks of another name, keeping their state and their content:
 * what //replacecolor and //typereplace share.
 */
final class Replacement {

    private Replacement() {
    }

    /**
     * @param rename the new names of a block name without namespace, best first (red_bed: lime_bed), empty to leave it
     * @return the first block of the new names that exists, with the same properties, or null if there is none
     */
    @Nullable
    static BlockState renamed(BlockState state, Function<String, List<String>> rename) {
        String id = state.getBlockType().id();
        int colon = id.indexOf(':');
        BlockType type = null;
        for (String name : rename.apply(id.substring(colon + 1))) {
            type = BlockTypes.get(id.substring(0, colon + 1) + name);
            if (type != null) {
                break;
            }
        }
        if (type == null) {
            return null;
        }
        BlockState result = type.getDefaultState();
        Map<String, ? extends Property<?>> properties = type.getPropertyMap();
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            @SuppressWarnings("unchecked")
            Property<Object> property = (Property<Object>) properties.get(entry.getKey().getName());
            if (property != null && property.getValues().contains(entry.getValue())) {
                result = result.with(property, entry.getValue());
            }
        }
        return result;
    }

    /**
     * @param messages the section of messages.yml with no-selection
     * @return the player's selection, or null after telling them they have none
     */
    @Nullable
    static Region selection(Player player, String messages) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        try {
            return WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld()).clone();
        } catch (IncompleteRegionException e) {
            Messages.send(player, messages + ".no-selection");
            return null;
        }
    }

    /**
     * Renames the blocks of the player's selection, in the background, as one edit undone with //undo.
     *
     * @param messages     the section of messages.yml with no-selection, done, none and failed
     * @param placeholders added to the done and none messages
     */
    static void apply(Plugin plugin, Player player, String command, String messages, Function<String, List<String>> rename,
                      Object... placeholders) {
        Region region = selection(player, messages);
        if (region == null) {
            return;
        }
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EditSession editSession = session.createEditSession(actor, command);
            try {
                // The renamed block of each state, or the state itself when it has none
                Map<BlockState, BlockState> renamed = new HashMap<>();
                for (BlockVector3 position : region) {
                    BaseBlock block = editSession.getFullBlock(position);
                    BlockState state = block.toImmutableState();
                    BlockState target = renamed.computeIfAbsent(state, s -> {
                        BlockState other = renamed(s, rename);
                        return other == null ? s : other;
                    });
                    if (target == state) {
                        continue;
                    }
                    // Keeps the content of shulker boxes and chests, the patterns of banners...
                    LazyReference<LinCompoundTag> nbt = block.getNbtReference();
                    if (nbt == null) {
                        editSession.setBlock(position, target);
                    } else {
                        editSession.setBlock(position, target.toBaseBlock(nbt));
                    }
                }
            } catch (WorldEditException e) {
                Messages.send(player, messages + ".failed", "message", e.getMessage());
            } catch (RuntimeException e) {
                Messages.send(player, messages + ".failed", "message", e);
                plugin.getLogger().log(Level.WARNING, "/" + command + " failed", e);
            } finally {
                editSession.close();
                session.remember(editSession);
            }
            Object[] values = new Object[placeholders.length + 2];
            values[0] = "count";
            values[1] = editSession.getBlockChangeCount();
            System.arraycopy(placeholders, 0, values, 2, placeholders.length);
            Messages.send(player, messages + (editSession.getBlockChangeCount() > 0 ? ".done" : ".none"), values);
        });
    }

    /**
     * A menu item without italics nor tooltip details.
     */
    static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}

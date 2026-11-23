package fr.darkvodou.facets.wand;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.DistanceWand;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the //farwand tools of the players after they leave and after a restart:
 * WorldEdit forgets every bound tool with the session, it only gives back the selection and navigation wands.
 * <p>
 * The items holding a farwand are written in the player's data when they leave (or when the plugin stops,
 * before the server saves the players), and bound again when they join. A farwand bound to the //wand item
 * stays a farwand: WorldEdit only puts the selection wand back on an item with no tool.
 */
public final class Farwands implements Listener {

    private final NamespacedKey key;

    public Farwands(Plugin plugin) {
        key = new NamespacedKey(plugin, "farwands");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        save(event.getPlayer());
    }

    /**
     * Binds the farwands of the players already online (after a plugin reload).
     */
    public void restoreOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restore(player);
        }
    }

    /**
     * Writes the farwands of the online players, before the server saves them on stop.
     */
    public void saveOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            save(player);
        }
    }

    private void restore(Player player) {
        List<String> items = player.getPersistentDataContainer().get(key, PersistentDataType.LIST.strings());
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        for (String id : items) {
            ItemType type = ItemTypes.get(id);
            if (type == null || tool(session, type) instanceof DistanceWand) {
                continue;
            }
            try {
                // Without the player, so the tool goes to the session's tools and not to FAWE's persistent brushes
                session.setTool(new BaseItem(type), new DistanceWand());
            } catch (InvalidToolBindException ignored) {
                // The item can no longer hold a tool
            }
        }
    }

    /**
     * Writes the items holding a farwand, or removes the entry when there are none
     * (the player bound something else on them, or used /none).
     */
    private void save(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player));
        if (session == null) {
            return;
        }
        List<String> items = new ArrayList<>();
        for (ItemType type : ItemType.REGISTRY.values()) {
            if (tool(session, type) instanceof DistanceWand) {
                items.add(type.id());
            }
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (items.isEmpty()) {
            data.remove(key);
        } else {
            data.set(key, PersistentDataType.LIST.strings(), items);
        }
    }

    @SuppressWarnings("deprecation")
    private static Tool tool(LocalSession session, ItemType type) {
        // The tool bound to the item type, without loading the default wands
        return session.getTool(type);
    }
}

package fr.darkvodou.facets.gravity;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.EventHandler;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import fr.darkvodou.facets.FaweExtents;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The players with the gravity on: the falling blocks of their FAWE edits fall, through {@link GravityExtent}.
 * Saved in gravity.yml, so the gravity stays on after a restart.
 */
public final class Gravity {

    public static final String PERMISSION = "facets.gravity";

    private final Plugin plugin;
    private final File file;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();

    public Gravity(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gravity.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getStringList("players")) {
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("gravity.yml: not a player UUID: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("players", new ArrayList<>(players.stream().map(UUID::toString).sorted().toList()));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save gravity.yml", e);
        }
    }

    /**
     * @return true if the gravity is now on for the player
     */
    public boolean toggle(UUID player) {
        boolean on = !players.remove(player);
        if (on) {
            players.add(player);
        }
        save();
        return on;
    }

    /**
     * FAWE only keeps the extents of the plugins it is told about.
     */
    public static void allowExtent() {
        FaweExtents.allow(GravityExtent.class);
    }

    // Early: set before the symmetry's extent, which then sits above it, so that the symmetric copies fall too
    @Subscribe(priority = EventHandler.Priority.EARLY)
    public void onEditSession(EditSessionEvent event) {
        // Above the history so the falls are undone with the edit. Undo and redo write below it, so they do not fall.
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY || event.getWorld() == null) {
            return;
        }
        Actor actor = event.getActor();
        if (actor != null && actor.isPlayer() && players.contains(actor.getUniqueId())
                && actor.hasPermission(PERMISSION)) {
            event.setExtent(new GravityExtent(event.getExtent()));
        }
    }
}

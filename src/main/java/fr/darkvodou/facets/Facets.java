package fr.darkvodou.facets;

import com.sk89q.worldedit.WorldEdit;
import fr.darkvodou.facets.gravity.Gravity;
import fr.darkvodou.facets.loft.LoftConfig;
import fr.darkvodou.facets.loft.LoftVisualizer;
import fr.darkvodou.facets.mask.Masks;
import fr.darkvodou.facets.menu.MaskFavorites;
import fr.darkvodou.facets.menu.MenuListener;
import fr.darkvodou.facets.symmetry.SymmetryConfig;
import fr.darkvodou.facets.symmetry.SymmetryListener;
import fr.darkvodou.facets.symmetry.SymmetryMarker;
import fr.darkvodou.facets.symmetry.SymmetrySessions;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Facets extends JavaPlugin {

    private static final String CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";

    private CommandManager commands;
    private LoftVisualizer visualizer;
    private BukkitTask visualizerTask;
    private SymmetrySessions symmetries;
    private SymmetryListener symmetryListener;
    private Gravity gravity;
    private BukkitTask markerTask;
    private MenuListener menu;

    @Override
    public void onEnable() {
        WorldEdit worldEdit = WorldEdit.getInstance();
        Masks.register(worldEdit, getLogger());

        ConfigFiles.update(this, CONFIG_FILE);
        reloadConfig();
        loadMessages();
        LoftConfig loftConfig = loadLoftConfig(new ArrayList<>());
        visualizer = new LoftVisualizer(loftConfig);
        scheduleVisualizer(loftConfig);

        symmetries = new SymmetrySessions(loadSymmetryConfig(new ArrayList<>()));
        symmetryListener = new SymmetryListener(this, symmetries);
        SymmetryListener.allowExtent();
        worldEdit.getEventBus().register(symmetryListener);
        listen(symmetryListener);
        scheduleMarker(symmetries.config());

        gravity = new Gravity(this);
        Gravity.allowExtent();
        worldEdit.getEventBus().register(gravity);

        menu = new MenuListener(this, symmetries, new MaskFavorites(this), loadMenuItem(new ArrayList<>()));
        listen(menu);

        commands = new CommandManager(this);
        commands.registerAll(visualizer, loftConfig, symmetries, gravity);
        loadCommandPriority(new ArrayList<>());
    }

    void listen(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (symmetryListener != null) {
            WorldEdit.getInstance().getEventBus().unregister(symmetryListener);
        }
        if (gravity != null) {
            WorldEdit.getInstance().getEventBus().unregister(gravity);
        }
    }

    /**
     * Reads config.yml again and applies it.
     *
     * @return the problems found in the configuration (invalid values are replaced by their default)
     */
    List<String> reload() {
        ConfigFiles.update(this, CONFIG_FILE);
        reloadConfig();
        loadMessages();
        List<String> warnings = new ArrayList<>();
        LoftConfig config = loadLoftConfig(warnings);
        visualizer.setConfig(config);
        commands.setLoftConfig(config);
        scheduleVisualizer(config);
        SymmetryConfig symmetryConfig = loadSymmetryConfig(warnings);
        symmetries.setConfig(symmetryConfig);
        scheduleMarker(symmetryConfig);
        menu.setItem(loadMenuItem(warnings));
        loadCommandPriority(warnings);
        return warnings;
    }

    /**
     * Reads messages.yml, after adding the new bundled messages to it.
     */
    private void loadMessages() {
        YamlConfiguration bundled = ConfigFiles.update(this, MESSAGES_FILE);
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), MESSAGES_FILE));
        messages.setDefaults(bundled);
        Messages.load(messages);
    }

    private LoftConfig loadLoftConfig(List<String> warnings) {
        return LoftConfig.load(getConfig(), warn(warnings));
    }

    private SymmetryConfig loadSymmetryConfig(List<String> warnings) {
        return SymmetryConfig.load(getConfig(), warn(warnings));
    }

    private Material loadMenuItem(List<String> warnings) {
        String name = getConfig().getString("menu.item", "nether_star");
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            warn(warnings).accept("menu.item: unknown item '" + name + "', using nether_star");
            return Material.NETHER_STAR;
        }
        return material;
    }

    private void loadCommandPriority(List<String> warnings) {
        commands.setPriority(getConfig().getStringList("commands.priority"), warn(warnings));
    }

    /**
     * Collects a config.yml problem and logs it.
     */
    private Consumer<String> warn(List<String> warnings) {
        return warning -> {
            warnings.add(warning);
            getLogger().warning("config.yml: " + warning);
        };
    }

    private void scheduleMarker(SymmetryConfig config) {
        markerTask = reschedule(markerTask, new SymmetryMarker(symmetries), config.markerTicks());
    }

    private void scheduleVisualizer(LoftConfig config) {
        visualizerTask = reschedule(visualizerTask, visualizer, config.refreshTicks());
    }

    /**
     * Cancels the previous task, if any, and runs the given one every {@code ticks} ticks.
     */
    private BukkitTask reschedule(BukkitTask previous, Runnable task, long ticks) {
        if (previous != null) {
            previous.cancel();
        }
        return getServer().getScheduler().runTaskTimer(this, task, ticks, ticks);
    }
}

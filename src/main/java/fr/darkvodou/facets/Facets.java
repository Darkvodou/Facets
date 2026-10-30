package fr.darkvodou.facets;

import com.sk89q.worldedit.WorldEdit;
import fr.darkvodou.facets.fill.FuzzyFillCommand;
import fr.darkvodou.facets.loft.LoftCommand;
import fr.darkvodou.facets.loft.LoftConfig;
import fr.darkvodou.facets.loft.LoftVisualizer;
import fr.darkvodou.facets.loft.RemovePositionCommand;
import fr.darkvodou.facets.mask.PanesMaskParser;
import fr.darkvodou.facets.menu.MaskFavorites;
import fr.darkvodou.facets.menu.MenuListener;
import fr.darkvodou.facets.symmetry.SymmetryCommand;
import fr.darkvodou.facets.symmetry.SymmetryConfig;
import fr.darkvodou.facets.symmetry.SymmetryListener;
import fr.darkvodou.facets.symmetry.SymmetryMarker;
import fr.darkvodou.facets.symmetry.SymmetrySessions;
import fr.darkvodou.facets.variants.VariantsMenu;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Facets extends JavaPlugin {

    private static final String CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";

    private LoftVisualizer visualizer;
    private LoftCommand loftCommand;
    private BukkitTask visualizerTask;
    private SymmetrySessions symmetries;
    private SymmetryListener symmetryListener;
    private BukkitTask markerTask;
    private MenuListener menu;

    @Override
    public void onEnable() {
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getMaskFactory().register(new PanesMaskParser(worldEdit));
        getLogger().info("Registered #panes mask.");

        ConfigFiles.update(this, CONFIG_FILE);
        reloadConfig();
        loadMessages();
        LoftConfig config = loadLoftConfig(new ArrayList<>());
        visualizer = new LoftVisualizer(config);
        scheduleVisualizer(config);
        loftCommand = new LoftCommand(this, visualizer, config);
        getServer().getPluginManager().registerEvents(loftCommand, this);
        RemovePositionCommand removePosition = new RemovePositionCommand();
        // Arceon also has a //loft: ours goes first
        CommandPriority priority = new CommandPriority(this, List.of(
                register("/loft", loftCommand),
                register("/rpos1", removePosition),
                register("/rpos2", removePosition)));
        getServer().getPluginManager().registerEvents(priority, this);
        // Runs once the server has started, after the other plugins have registered their commands
        getServer().getScheduler().runTask(this, priority::takeOver);
        FuzzyFillCommand fuzzyFill = new FuzzyFillCommand(this);
        register("/fuzzyfill", fuzzyFill);
        register("/fuzzyfillh", fuzzyFill);
        register("/fuzzyfillr", fuzzyFill);
        symmetries = new SymmetrySessions(loadSymmetryConfig(new ArrayList<>()));
        symmetryListener = new SymmetryListener(this, symmetries);
        SymmetryListener.allowExtent();
        worldEdit.getEventBus().register(symmetryListener);
        getServer().getPluginManager().registerEvents(symmetryListener, this);
        register("/sym", new SymmetryCommand(symmetries));
        scheduleMarker(symmetries.config());
        menu = new MenuListener(this, symmetries, new MaskFavorites(this), loadMenuItem(new ArrayList<>()));
        getServer().getPluginManager().registerEvents(menu, this);
        VariantsMenu variants = new VariantsMenu(getLogger());
        getServer().getPluginManager().registerEvents(variants, this);
        register("variant", variants);
        register("family", variants);
        register("facets", new FacetsCommand(this));
        getLogger().info("Registered loft selection, //fuzzyfill, //sym, /variant, /family and the menu.");
    }

    @Override
    public void onDisable() {
        if (symmetryListener != null) {
            WorldEdit.getInstance().getEventBus().unregister(symmetryListener);
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
        loftCommand.setConfig(config);
        scheduleVisualizer(config);
        SymmetryConfig symmetryConfig = loadSymmetryConfig(warnings);
        symmetries.setConfig(symmetryConfig);
        scheduleMarker(symmetryConfig);
        menu.setItem(loadMenuItem(warnings));
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
        return LoftConfig.load(getConfig(), warning -> {
            warnings.add(warning);
            getLogger().warning("config.yml: " + warning);
        });
    }

    private SymmetryConfig loadSymmetryConfig(List<String> warnings) {
        return SymmetryConfig.load(getConfig(), warning -> {
            warnings.add(warning);
            getLogger().warning("config.yml: " + warning);
        });
    }

    private Material loadMenuItem(List<String> warnings) {
        String name = getConfig().getString("menu.item", "nether_star");
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            String warning = "menu.item: unknown item '" + name + "', using nether_star";
            warnings.add(warning);
            getLogger().warning("config.yml: " + warning);
            return Material.NETHER_STAR;
        }
        return material;
    }

    private void scheduleMarker(SymmetryConfig config) {
        if (markerTask != null) {
            markerTask.cancel();
        }
        markerTask = getServer().getScheduler().runTaskTimer(this, new SymmetryMarker(symmetries),
                config.markerTicks(), config.markerTicks());
    }

    private void scheduleVisualizer(LoftConfig config) {
        if (visualizerTask != null) {
            visualizerTask.cancel();
        }
        visualizerTask = getServer().getScheduler().runTaskTimer(this, visualizer, config.refreshTicks(), config.refreshTicks());
    }

    private PluginCommand register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command " + name + " is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        return command;
    }
}

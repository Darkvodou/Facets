package fr.darkvodou.facets;

import fr.darkvodou.facets.fill.FuzzyFillCommand;
import fr.darkvodou.facets.gravity.Gravity;
import fr.darkvodou.facets.gravity.GravityCommand;
import fr.darkvodou.facets.loft.LoftCommand;
import fr.darkvodou.facets.loft.LoftConfig;
import fr.darkvodou.facets.loft.LoftVisualizer;
import fr.darkvodou.facets.loft.RemovePositionCommand;
import fr.darkvodou.facets.replace.ReplaceColorCommand;
import fr.darkvodou.facets.replace.ReplaceTypeCommand;
import fr.darkvodou.facets.symmetry.SymmetryCommand;
import fr.darkvodou.facets.symmetry.SymmetrySessions;
import fr.darkvodou.facets.variants.VariantsMenu;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates every Facets command and binds it to its plugin.yml entry.
 */
final class CommandManager {

    private final Facets plugin;
    private final List<PluginCommand> commands = new ArrayList<>();
    private LoftCommand loft;
    private CommandPriority priority;

    CommandManager(Facets plugin) {
        this.plugin = plugin;
    }

    void registerAll(LoftVisualizer visualizer, LoftConfig loftConfig, SymmetrySessions symmetries, Gravity gravity) {
        loft = new LoftCommand(plugin, visualizer, loftConfig);
        plugin.listen(loft);
        register(loft, "/loft");
        register(new RemovePositionCommand(), "/rpos1", "/rpos2");
        register(new FuzzyFillCommand(plugin), "/fuzzyfill", "/fuzzyfillr", "/fuzzyfills");
        register(new SymmetryCommand(symmetries), "/sym");
        register(new GravityCommand(gravity), "/gravity");
        ReplaceColorCommand replaceColor = new ReplaceColorCommand(plugin);
        plugin.listen(replaceColor);
        register(replaceColor, "/replacecolor");
        ReplaceTypeCommand replaceType = new ReplaceTypeCommand(plugin);
        plugin.listen(replaceType);
        register(replaceType, "/typereplace");
        VariantsMenu variants = new VariantsMenu(plugin.getLogger());
        plugin.listen(variants);
        register(variants, "variant", "family");
        register(new FacetsCommand(plugin), "facets");

        priority = new CommandPriority(plugin, commands);
        plugin.listen(priority);
        // Runs once the server has started, after the other plugins have registered their commands
        plugin.getServer().getScheduler().runTask(plugin, priority::takeOver);
        plugin.getLogger().info("Registered commands: "
                + String.join(", ", commands.stream().map(command -> "/" + command.getName()).toList()) + ".");
    }

    void setLoftConfig(LoftConfig config) {
        loft.setConfig(config);
    }

    /**
     * The commands going before the ones of other plugins with the same name (commands.priority in config.yml).
     */
    void setPriority(List<String> entries, Consumer<String> warn) {
        priority.setPriority(entries, warn);
    }

    /**
     * Sets the executor and tab completer of each given plugin.yml command.
     */
    private void register(TabExecutor executor, String... names) {
        for (String name : names) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                throw new IllegalStateException("Command " + name + " is missing from plugin.yml");
            }
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            commands.add(command);
        }
    }
}

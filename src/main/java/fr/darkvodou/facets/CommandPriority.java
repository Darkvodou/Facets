package fr.darkvodou.facets;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gives the commands chosen in config.yml (commands.priority), with their aliases, priority over the ones of other
 * plugins with the same name (Arceon's //loft, //replacecolor...). The other command stays available with its plugin
 * prefix, for example /arceon:/loft, and gets its label back when it leaves the list.
 */
final class CommandPriority implements Listener {

    private final JavaPlugin plugin;
    private final List<PluginCommand> commands;
    private List<PluginCommand> prioritized = List.of();
    // Commands of other plugins whose label we took, to give it back
    private final Map<String, Command> replaced = new HashMap<>();
    private boolean started;

    CommandPriority(JavaPlugin plugin, List<PluginCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    /**
     * Chooses the commands that go first, by name or alias, with or without their slashes.
     */
    void setPriority(List<String> entries, Consumer<String> warn) {
        Set<String> wanted = new HashSet<>();
        entries.forEach(entry -> wanted.add(strip(entry)));
        Set<String> found = new HashSet<>();
        List<PluginCommand> chosen = new ArrayList<>();
        for (PluginCommand command : commands) {
            List<String> names = labels(command).stream().map(CommandPriority::strip).toList();
            if (names.stream().anyMatch(wanted::contains)) {
                chosen.add(command);
                found.addAll(names);
            }
        }
        for (String entry : entries) {
            if (!found.contains(strip(entry))) {
                warn.accept("commands.priority: unknown Facets command '" + entry + "'");
            }
        }
        prioritized = chosen;
        if (started) {
            apply();
        }
    }

    /**
     * Takes the labels once every plugin has registered its commands, so that completion uses ours too.
     */
    void takeOver() {
        started = true;
        apply();
    }

    private void apply() {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        Set<String> taken = new HashSet<>();
        prioritized.forEach(command -> taken.addAll(labels(command)));
        boolean changed = false;
        for (Iterator<Map.Entry<String, Command>> it = replaced.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Command> entry = it.next();
            if (!taken.contains(entry.getKey())) {
                known.put(entry.getKey(), entry.getValue());
                it.remove();
                changed = true;
                plugin.getLogger().info("/" + entry.getKey() + " runs " + owner(entry.getValue()) + " again.");
            }
        }
        for (PluginCommand command : prioritized) {
            for (String label : labels(command)) {
                Command current = known.get(label);
                if (current != command) {
                    known.put(label, command);
                    changed = true;
                    if (current != null) {
                        replaced.putIfAbsent(label, current);
                        plugin.getLogger().info("/" + label + " now runs Facets instead of " + owner(current) + ".");
                    }
                }
            }
        }
        if (changed) {
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    // In case a plugin takes the label back after us: send the command to ours with our prefix
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        int end = message.indexOf(' ');
        String label = (end < 0 ? message.substring(1) : message.substring(1, end)).toLowerCase(Locale.ROOT);
        for (PluginCommand command : prioritized) {
            if (labels(command).contains(label)) {
                String prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
                event.setMessage("/" + prefix + command.getName() + (end < 0 ? "" : message.substring(end)));
                return;
            }
        }
    }

    /**
     * The name and the aliases of the command, in lower case.
     */
    private static List<String> labels(PluginCommand command) {
        List<String> labels = new ArrayList<>();
        labels.add(command.getName().toLowerCase(Locale.ROOT));
        command.getAliases().forEach(alias -> labels.add(alias.toLowerCase(Locale.ROOT)));
        return labels;
    }

    /**
     * A label without its leading slashes, as players may write it with one more (//loft for the /loft command).
     */
    private static String strip(String label) {
        int start = 0;
        while (start < label.length() && label.charAt(start) == '/') {
            start++;
        }
        return label.substring(start).toLowerCase(Locale.ROOT);
    }

    private static String owner(Command command) {
        return command instanceof PluginCommand other ? other.getPlugin().getName() : command.getName();
    }
}

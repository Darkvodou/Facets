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

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gives our commands priority over the ones of other plugins with the same name (Arceon's //loft).
 * The other command stays available with its plugin prefix, for example /arceon:/loft.
 */
final class CommandPriority implements Listener {

    private final JavaPlugin plugin;
    private final List<PluginCommand> commands;

    CommandPriority(JavaPlugin plugin, List<PluginCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    /**
     * Takes the plain labels once every plugin has registered its commands, so that completion uses ours too.
     */
    void takeOver() {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        boolean changed = false;
        for (PluginCommand command : commands) {
            String label = command.getName().toLowerCase(Locale.ROOT);
            Command current = known.get(label);
            if (current != command) {
                known.put(label, command);
                changed = true;
                if (current != null) {
                    plugin.getLogger().info("/" + label + " now runs Facets instead of "
                            + (current instanceof PluginCommand other ? other.getPlugin().getName() : current.getName()) + ".");
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
        for (PluginCommand command : commands) {
            if (command.getName().equalsIgnoreCase(label)) {
                String prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
                event.setMessage("/" + prefix + command.getName() + (end < 0 ? "" : message.substring(end)));
                return;
            }
        }
    }
}

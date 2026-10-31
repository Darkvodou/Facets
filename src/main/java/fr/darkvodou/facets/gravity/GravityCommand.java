package fr.darkvodou.facets.gravity;

import fr.darkvodou.facets.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * //gravity: turns the gravity of the blocks set with FAWE on or off.
 */
public final class GravityCommand implements TabExecutor {

    private final Gravity gravity;

    public GravityCommand(Gravity gravity) {
        this.gravity = gravity;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        Messages.send(player, gravity.toggle(player.getUniqueId()) ? "gravity.on" : "gravity.off");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}

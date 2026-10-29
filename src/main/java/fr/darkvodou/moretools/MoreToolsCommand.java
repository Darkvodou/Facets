package fr.darkvodou.moretools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

/**
 * /moretools reload
 */
final class MoreToolsCommand implements TabExecutor {

    private final MoreTools plugin;

    MoreToolsCommand(MoreTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            Messages.send(sender, "usage", "usage", "/" + label + " reload");
            return true;
        }
        List<String> warnings = plugin.reload();
        Messages.send(sender, "reload.done");
        for (String warning : warnings) {
            Messages.send(sender, "reload.warning", "message", warning);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase()) ? List.of("reload") : List.of();
    }
}

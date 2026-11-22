package fr.darkvodou.facets.cover;

import fr.darkvodou.facets.Messages;
import fr.darkvodou.facets.brush.BrushCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * //fbrush fixcover &lt;radius&gt; [-r] [-n -s -e -w -u -d]: binds to the item in hand a FAWE brush doing //fixcover
 * on the sphere around the point clicked (right click). Its size, range and masks are set with FAWE's commands (/size,
 * /range, /mask).
 */
public final class FixCoverBrushCommand implements TabExecutor {

    public static final String PERMISSION = "facets.fixcover";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        CoverOptions options = args.length > 0 && args[0].equalsIgnoreCase("fixcover")
                ? CoverOptions.parse(args, 1) : null;
        if (options == null || options.radius() == null) {
            Messages.send(player, "usage", "usage", "/" + label + " fixcover <radius> " + CoverOptions.FLAGS);
            return true;
        }
        String radiusArg = options.radius();
        int radius;
        try {
            radius = Integer.parseInt(radiusArg);
        } catch (NumberFormatException e) {
            Messages.send(player, "fixcover.invalid-number", "value", radiusArg);
            return true;
        }
        if (radius < 1) {
            Messages.send(player, "fixcover.brush-radius-range");
            return true;
        }
        if (BrushCommand.bind(player, args, radius, PERMISSION, new FixCoverBrush(options), null)) {
            Messages.send(player, "fixcover.brush-bound", "radius", radius, "options", options.describe());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return CoverOptions.complete(args, 1, List.of("3", "5", "8", "12"));
    }
}

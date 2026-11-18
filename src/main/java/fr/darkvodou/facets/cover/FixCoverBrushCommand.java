package fr.darkvodou.facets.cover;

import fr.darkvodou.facets.Messages;
import fr.darkvodou.facets.brush.BrushCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * //fbrush fixcover &lt;radius&gt; [-r]: binds to the item in hand a FAWE brush doing //fixcover on the sphere around
 * the point clicked (right click). Its size, range and masks are set with FAWE's commands (/size, /range, /mask).
 */
public final class FixCoverBrushCommand implements TabExecutor {

    public static final String PERMISSION = "facets.fixcover";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        boolean random = false;
        String radiusArg = null;
        boolean valid = args.length > 0 && args[0].equalsIgnoreCase("fixcover");
        for (int i = 1; i < args.length && valid; i++) {
            if (isRandomFlag(args[i])) {
                random = true;
            } else if (radiusArg == null) {
                radiusArg = args[i];
            } else {
                valid = false;
            }
        }
        if (!valid || radiusArg == null) {
            Messages.send(player, "usage", "usage", "/" + label + " fixcover <radius> [-r]");
            return true;
        }
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
        if (BrushCommand.bind(player, args, radius, PERMISSION, new FixCoverBrush(random), null)) {
            Messages.send(player, random ? "fixcover.brush-bound-random" : "fixcover.brush-bound", "radius", radius);
        }
        return true;
    }

    private static boolean isRandomFlag(String arg) {
        return arg.equalsIgnoreCase("-r") || arg.equalsIgnoreCase("-random");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return List.of();
        }
        boolean hasRadius = false;
        boolean hasFlag = false;
        for (int i = 1; i < args.length - 1; i++) {
            if (isRandomFlag(args[i])) {
                hasFlag = true;
            } else {
                hasRadius = true;
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if (!hasRadius) {
            suggestions.addAll(List.of("3", "5", "8", "12"));
        }
        if (!hasFlag) {
            suggestions.add("-r");
        }
        return suggestions.stream().filter(suggestion -> suggestion.startsWith(last)).toList();
    }
}

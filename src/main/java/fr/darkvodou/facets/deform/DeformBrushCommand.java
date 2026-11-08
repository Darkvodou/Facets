package fr.darkvodou.facets.deform;

import com.fastasyncworldedit.core.command.tool.brush.BrushSettings;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.brush.Brush;
import fr.darkvodou.facets.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * //fbrush magnet [radius] [force] [-m &lt;mask&gt;] [-t]: binds to the item in hand a FAWE brush doing
 * //fdeform magnet in two clicks: a left click chooses the zone (the sphere around the point clicked), a right click
 * pulls it toward the point clicked. The points are found with /range and /tracemask. Its size, range, masks and trace
 * mask are set with FAWE's commands (/size, /range, /mask, /smask, /tracemask).
 */
public final class DeformBrushCommand implements TabExecutor {

    private static final String USAGE = " magnet [radius] [force] [-m <mask>] [-t]";
    private static final int DEFAULT_RADIUS = 5;
    static final String PERMISSION = "facets.fdeform";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        String usage = "/" + label + USAGE;
        if (args.length == 0 || !args[0].equalsIgnoreCase("magnet")) {
            Messages.send(player, "usage", "usage", usage);
            return true;
        }
        MagnetOptions options = MagnetOptions.parse(player, args, 1, 2, usage);
        if (options == null) {
            return true;
        }
        int radius = options.numbers().isEmpty() ? DEFAULT_RADIUS : options.numbers().get(0);
        if (radius < 1) {
            Messages.send(player, "fdeform.radius-range");
            return true;
        }
        Integer force = MagnetOptions.force(player, options.numbers().size() < 2 ? null : options.numbers().get(1));
        if (force == null) {
            return true;
        }

        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        try {
            WorldEdit.getInstance().checkMaxBrushRadius(radius, actor);
            // Like FAWE's own brush commands: the item gets a new brush tool
            session.setTool(actor, null);
            BrushTool tool = session.getBrushTool(actor);
            // FAWE's primary action is the right click, its secondary one the left click
            MagnetBrush.Zone zone = new MagnetBrush.Zone();
            tool.setPrimary(settings(args, radius, new MagnetBrush(zone, force, options.mask(), options.trim())));
            tool.setSecondary(settings(args, radius, new MagnetBrush.Select(zone)));
        } catch (WorldEditException e) {
            actor.printError(e.getRichMessage());
            return true;
        }
        Messages.send(player, "fdeform.brush-bound", "radius", radius, "force", force);
        return true;
    }

    private static BrushSettings settings(String[] args, int radius, Brush brush) {
        BrushSettings settings = new BrushSettings();
        settings.addSetting(BrushSettings.SettingType.BRUSH, "fbrush " + String.join(" ", args));
        settings.addPermission(PERMISSION);
        settings.setBrush(brush);
        settings.setSize(radius);
        return settings;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return "magnet".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("magnet") : List.of();
        }
        if (!args[0].equalsIgnoreCase("magnet")) {
            return List.of();
        }
        return MagnetOptions.complete(sender, args, 1,
                List.of(List.of("3", "5", "8", "12"), List.of("25", "50", "75", "100")));
    }
}

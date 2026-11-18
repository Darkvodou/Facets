package fr.darkvodou.facets.brush;

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

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * //fbrush &lt;brush&gt; ...: binds one of the Facets brushes to the item in hand. Each brush has its own command, given
 * the whole arguments (the brush name first), and its own permission.
 */
public final class BrushCommand implements TabExecutor {

    private record Entry(String permission, TabExecutor executor) {
    }

    private final Map<String, Entry> brushes = new LinkedHashMap<>();

    public BrushCommand add(String name, String permission, TabExecutor executor) {
        brushes.put(name, new Entry(permission, executor));
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Entry entry = args.length == 0 ? null : brushes.get(args[0].toLowerCase(Locale.ROOT));
        if (entry == null) {
            List<String> names = names(sender);
            Messages.send(sender, "usage", "usage",
                    "/" + label + " <" + String.join(" | ", names.isEmpty() ? brushes.keySet() : names) + ">");
            return true;
        }
        if (!sender.hasPermission(entry.permission())) {
            Messages.send(sender, "no-permission", "permission", entry.permission());
            return true;
        }
        return entry.executor().onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return names(sender).stream().filter(name -> name.startsWith(start)).toList();
        }
        Entry entry = brushes.get(args[0].toLowerCase(Locale.ROOT));
        if (entry == null || !sender.hasPermission(entry.permission())) {
            return List.of();
        }
        return entry.executor().onTabComplete(sender, command, label, args);
    }

    private List<String> names(CommandSender sender) {
        return brushes.entrySet().stream()
                .filter(brush -> sender.hasPermission(brush.getValue().permission()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Binds to the item in hand a new FAWE brush tool, whose size, range and masks are then set with FAWE's commands.
     * FAWE's primary action is the right click, its secondary one the left click.
     *
     * @return whether the brush was bound (otherwise the player was told why)
     */
    public static boolean bind(Player player, String[] args, int radius, String permission, Brush rightClick,
                               @Nullable Brush leftClick) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        try {
            WorldEdit.getInstance().checkMaxBrushRadius(radius, actor);
            // Like FAWE's own brush commands: the item gets a new brush tool
            session.setTool(actor, null);
            BrushTool tool = session.getBrushTool(actor);
            tool.setPrimary(settings(args, radius, permission, rightClick));
            if (leftClick != null) {
                tool.setSecondary(settings(args, radius, permission, leftClick));
            }
        } catch (WorldEditException e) {
            actor.printError(e.getRichMessage());
            return false;
        }
        return true;
    }

    private static BrushSettings settings(String[] args, int radius, String permission, Brush brush) {
        BrushSettings settings = new BrushSettings();
        settings.addSetting(BrushSettings.SettingType.BRUSH, "fbrush " + String.join(" ", args));
        settings.addPermission(permission);
        settings.setBrush(brush);
        settings.setSize(radius);
        return settings;
    }
}

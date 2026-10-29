package fr.darkvodou.moretools.loft;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import fr.darkvodou.moretools.Messages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * //rpos1 and //rpos2 [amount] [-r | -c]: remove the last frame (pos1) or the last point (pos2)
 * of the loft selection, or the closest one to the player with -r / -c (-nearest / -closest).
 */
public final class RemovePositionCommand implements TabExecutor {

    // Both flags do the same
    private static final Flags FLAGS = new Flags('r', "nearest", 'c', "closest");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        boolean primary = command.getName().equalsIgnoreCase("/rpos1");
        boolean nearest = false;
        int amount = 1;
        for (String arg : args) {
            String letters = FLAGS.parse(arg);
            if (letters != null) {
                if (!letters.chars().allMatch(c -> FLAGS.isKnown((char) c))) {
                    Messages.send(player, "usage", "usage", "/" + label + " [amount] [-r | -c]");
                    return true;
                }
                nearest = true;
            } else {
                try {
                    amount = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                if (amount < 1) {
                    Messages.send(player, "usage", "usage", "/" + label + " [amount] [-r | -c]");
                    return true;
                }
            }
        }

        LoftRegionSelector selector = LoftSessions.selector(player);
        if (selector == null) {
            Messages.send(player, "loft.no-selection");
            return true;
        }
        LoftRegion loft = selector.getLoft();
        Location location = player.getLocation();
        Vector3 from = Vector3.at(location.getX(), location.getY(), location.getZ());

        int removed = 0;
        BlockVector3 last = null;
        for (int i = 0; i < amount; i++) {
            BlockVector3 position;
            if (primary) {
                position = nearest ? loft.removeNearestFrame(from) : loft.removeLastFrame();
            } else {
                position = nearest ? loft.removeNearestPoint(from) : loft.removeLastPoint();
            }
            if (position == null) {
                break;
            }
            removed++;
            last = position;
        }

        String what = primary ? "frame" : "point";
        if (removed == 0) {
            Messages.send(player, "loft.no-" + what);
        } else if (removed == 1) {
            Messages.send(player, "loft.removed-" + what, "position", last,
                    "frames", loft.getFrameCount(), "points", loft.getPointCount());
        } else {
            Messages.send(player, "loft.removed-" + what + "s", "count", removed,
                    "frames", loft.getFrameCount(), "points", loft.getPointCount());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1];
        boolean nearest = false;
        boolean amount = false;
        for (int i = 0; i < args.length - 1; i++) {
            if (FLAGS.parse(args[i]) != null) {
                nearest = true;
            } else {
                amount = true;
            }
        }
        List<String> options = new ArrayList<>();
        if (!nearest) {
            options.addAll(last.startsWith("-") ? FLAGS.complete(last, Set.of()) : FLAGS.all(Set.of()));
        }
        if (!amount) {
            options.addAll(List.of("1", "2", "5"));
        }
        String prefix = last.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}

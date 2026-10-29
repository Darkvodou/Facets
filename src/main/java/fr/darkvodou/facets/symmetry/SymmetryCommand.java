package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;
import fr.darkvodou.facets.Messages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * //sym [set [type] | type &lt;type&gt; | range &lt;blocks | max&gt; | toggle | off | info]
 */
public final class SymmetryCommand implements TabExecutor {

    private static final String USAGE = "//sym [set [type] | type <type> | range <blocks | max> | toggle | off | info]";

    private final SymmetrySessions sessions;

    public SymmetryCommand(SymmetrySessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "s" -> set(player, args);
            case "type", "t" -> type(player, args);
            case "range", "r" -> range(player, args);
            case "toggle" -> toggle(player);
            case "off", "clear", "remove" -> off(player);
            case "info", "i" -> info(player);
            default -> Messages.send(player, "usage", "usage", USAGE);
        }
        return true;
    }

    /**
     * //sym set [type]: puts the point at the player's feet, with the given type, else the previous one.
     */
    private void set(Player player, String[] args) {
        Symmetry previous = sessions.get(player.getUniqueId());
        SymmetryType type = previous != null ? previous.type() : sessions.config().defaultType();
        if (args.length >= 2) {
            type = type(player, args[1]);
            if (type == null) {
                return;
            }
        }
        Location feet = player.getLocation();
        Symmetry symmetry = new Symmetry(player.getWorld().getName(),
                BlockVector3.at(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ()), type,
                previous != null ? previous.range() : 0, true);
        sessions.set(player.getUniqueId(), symmetry);
        Messages.send(player, "symmetry.set", "type", type.id(), "position", position(symmetry),
                "copies", type.copies(), "range", symmetry.range(sessions.config().maxRange()));
    }

    private void type(Player player, String[] args) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "//sym type <type>");
            return;
        }
        SymmetryType type = type(player, args[1]);
        if (type != null) {
            sessions.set(player.getUniqueId(), symmetry.withType(type));
            Messages.send(player, "symmetry.type", "type", type.id(), "copies", type.copies());
        }
    }

    private void range(Player player, String[] args) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "//sym range <blocks | max>");
            return;
        }
        int max = sessions.config().maxRange();
        int range;
        if (args[1].equalsIgnoreCase("max")) {
            range = 0;
        } else {
            try {
                range = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Messages.send(player, "symmetry.invalid-number", "value", args[1]);
                return;
            }
            if (range < 1 || range > max) {
                Messages.send(player, "symmetry.range-limit", "max", max);
                return;
            }
        }
        Symmetry updated = symmetry.withRange(range);
        sessions.set(player.getUniqueId(), updated);
        Messages.send(player, "symmetry.range", "range", updated.range(max));
    }

    private void toggle(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null) {
            sessions.set(player.getUniqueId(), symmetry.withEnabled(!symmetry.enabled()));
            Messages.send(player, symmetry.enabled() ? "symmetry.paused" : "symmetry.resumed");
        }
    }

    private void off(Player player) {
        Messages.send(player, sessions.remove(player.getUniqueId()) != null ? "symmetry.removed" : "symmetry.none");
    }

    private void info(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null) {
            Messages.send(player, symmetry.enabled() ? "symmetry.info" : "symmetry.info-paused", "type", symmetry.type().id(),
                    "position", position(symmetry), "world", symmetry.world(), "copies", symmetry.type().copies(),
                    "range", symmetry.range(sessions.config().maxRange()));
        }
    }

    private Symmetry existing(Player player) {
        Symmetry symmetry = sessions.get(player.getUniqueId());
        if (symmetry == null) {
            Messages.send(player, "symmetry.none");
        }
        return symmetry;
    }

    private static SymmetryType type(Player player, String name) {
        SymmetryType type = SymmetryType.fromId(name);
        if (type == null) {
            Messages.send(player, "symmetry.unknown-type", "type", name, "types", String.join(", ", ids().toList()));
        }
        return type;
    }

    private static String position(Symmetry symmetry) {
        BlockVector3 center = symmetry.center();
        return center.x() + ", " + center.y() + ", " + center.z();
    }

    private static Stream<String> ids() {
        return Stream.of(SymmetryType.values()).map(SymmetryType::id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return Stream.of("set", "type", "range", "toggle", "off", "info").filter(s -> s.startsWith(last)).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set", "s", "type", "t" -> ids().filter(id -> id.startsWith(last)).toList();
                case "range", "r" -> Stream.of("16", "32", "64", "max").filter(s -> s.startsWith(last)).toList();
                default -> List.of();
            };
        }
        return List.of();
    }
}

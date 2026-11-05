package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;
import fr.darkvodou.facets.Messages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * //sym [set [type] | type &lt;type&gt; | range &lt;blocks | max&gt; | toggle | off | info] for the first symmetry,
 * //sym2 [set [type] | type &lt;type&gt; | off | info] for the second, which copies the first one too.
 * The range and the pause are those of the first symmetry, for both.
 */
public final class SymmetryCommand implements TabExecutor {

    private static final String USAGE = "//sym [set [type] | type <type> | range <blocks | max> | toggle | off | info]";
    private static final String USAGE_SECOND = "//sym2 [set [type] | type <type> | off | info]";

    private final SymmetrySessions sessions;
    // //sym2: acts on the second symmetry
    private final boolean second;

    public SymmetryCommand(SymmetrySessions sessions, boolean second) {
        this.sessions = sessions;
        this.second = second;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (second) {
            switch (sub) {
                case "set", "s" -> setSecond(player, args);
                case "type", "t" -> typeSecond(player, args);
                case "off", "clear", "remove" -> offSecond(player);
                case "info", "i" -> infoSecond(player);
                default -> Messages.send(player, "usage", "usage", USAGE_SECOND);
            }
            return true;
        }
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
        String world = player.getWorld().getName();
        // The second symmetry stays when the first moves in its world
        Symmetry.Second second = previous != null && previous.world().equals(world) ? previous.second() : null;
        if (!combinable(player, type, second)) {
            return;
        }
        Symmetry symmetry = new Symmetry(world, feet(player), type, previous != null ? previous.range() : 0, true, second);
        sessions.set(player.getUniqueId(), symmetry);
        Messages.send(player, "symmetry.set", "type", type.id(), "position", position(symmetry.center()),
                "copies", symmetry.copyCount(), "range", symmetry.range(sessions.config().maxRange()));
    }

    /**
     * //sym2 set [type]: puts the second point at the player's feet, with the given type, else the previous one.
     * It copies the blocks and the copies of the first symmetry.
     */
    private void setSecond(Player player, String[] args) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        if (!symmetry.world().equals(player.getWorld().getName())) {
            Messages.send(player, "symmetry.other-world", "world", symmetry.world());
            return;
        }
        SymmetryType type = symmetry.second() != null ? symmetry.second().type() : sessions.config().defaultType();
        if (args.length >= 2) {
            type = type(player, args[1]);
            if (type == null) {
                return;
            }
        }
        Symmetry.Second second = new Symmetry.Second(feet(player), type);
        if (!combinable(player, symmetry.type(), second)) {
            return;
        }
        Symmetry updated = symmetry.withSecond(second);
        sessions.set(player.getUniqueId(), updated);
        Messages.send(player, "symmetry.added", "type", type.id(), "position", position(second.center()),
                "copies", updated.copyCount());
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
        if (type != null && combinable(player, type, symmetry.second())) {
            Symmetry updated = symmetry.withType(type);
            sessions.set(player.getUniqueId(), updated);
            Messages.send(player, "symmetry.type", "type", type.id(), "copies", updated.copyCount());
        }
    }

    /**
     * //sym2 type &lt;type&gt;: changes the type of the second symmetry, without moving its point.
     */
    private void typeSecond(Player player, String[] args) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry == null) {
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "//sym2 type <type>");
            return;
        }
        SymmetryType type = type(player, args[1]);
        if (type == null) {
            return;
        }
        Symmetry.Second changed = new Symmetry.Second(symmetry.second().center(), type);
        if (combinable(player, symmetry.type(), changed)) {
            Symmetry updated = symmetry.withSecond(changed);
            sessions.set(player.getUniqueId(), updated);
            Messages.send(player, "symmetry.type-second", "type", type.id(), "copies", updated.copyCount());
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

    /**
     * //sym off: removes both symmetries.
     */
    private void off(Player player) {
        Messages.send(player, sessions.remove(player.getUniqueId()) != null ? "symmetry.removed" : "symmetry.none");
    }

    /**
     * //sym2 off: removes the second symmetry, keeping the first.
     */
    private void offSecond(Player player) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry != null) {
            sessions.set(player.getUniqueId(), symmetry.withSecond(null));
            Messages.send(player, "symmetry.second-removed");
        }
    }

    private void info(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null) {
            Messages.send(player, symmetry.enabled() ? "symmetry.info" : "symmetry.info-paused", "type", symmetry.type().id(),
                    "position", position(symmetry.center()), "world", symmetry.world(), "copies", symmetry.copyCount(),
                    "range", symmetry.range(sessions.config().maxRange()));
            if (symmetry.second() != null) {
                infoSecond(player, symmetry);
            }
        }
    }

    private void infoSecond(Player player) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry != null) {
            infoSecond(player, symmetry);
        }
    }

    private static void infoSecond(Player player, Symmetry symmetry) {
        Symmetry.Second second = symmetry.second();
        Messages.send(player, "symmetry.info-second", "type", second.type().id(), "position", position(second.center()),
                "copies", symmetry.copyCount());
    }

    @Nullable
    private Symmetry existing(Player player) {
        Symmetry symmetry = sessions.get(player.getUniqueId());
        if (symmetry == null) {
            Messages.send(player, "symmetry.none");
        }
        return symmetry;
    }

    /**
     * @return the player's symmetry if it has a second one, else null after telling them
     */
    @Nullable
    private Symmetry existingSecond(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null && symmetry.second() == null) {
            Messages.send(player, "symmetry.no-second");
            return null;
        }
        return symmetry;
    }

    @Nullable
    private static SymmetryType type(Player player, String name) {
        SymmetryType type = SymmetryType.fromId(name);
        if (type == null) {
            Messages.send(player, "symmetry.unknown-type", "type", name, "types", String.join(", ", ids().toList()));
        }
        return type;
    }

    /**
     * @return whether the two types can be combined, telling the player when not
     */
    private static boolean combinable(Player player, SymmetryType first, @Nullable Symmetry.Second second) {
        if (second == null || Symmetry.combinable(first, second.type())) {
            return true;
        }
        Messages.send(player, "symmetry.too-many", "first", first.id(), "second", second.type().id(),
                "copies", first.copies() * second.type().copies(), "max", Symmetry.MAX_COPIES);
        return false;
    }

    private static BlockVector3 feet(Player player) {
        Location feet = player.getLocation();
        return BlockVector3.at(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
    }

    private static String position(BlockVector3 center) {
        return center.x() + ", " + center.y() + ", " + center.z();
    }

    private static Stream<String> ids() {
        return Stream.of(SymmetryType.values()).map(SymmetryType::id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            Stream<String> subs = second ? Stream.of("set", "type", "off", "info")
                    : Stream.of("set", "type", "range", "toggle", "off", "info");
            return subs.filter(s -> s.startsWith(last)).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set", "s", "type", "t" -> ids().filter(id -> id.startsWith(last)).toList();
                case "range", "r" -> second ? List.of()
                        : Stream.of("16", "32", "64", "max").filter(s -> s.startsWith(last)).toList();
                default -> List.of();
            };
        }
        return List.of();
    }
}

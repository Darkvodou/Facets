package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.math.BlockVector3;
import fr.darkvodou.facets.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * //sym [set [type] [even | odd] | placeaim [type] [even | odd] | type &lt;type&gt; | add &lt;plane&gt;
 * | remove [plane] | range &lt;blocks | max&gt; | swap | share [player] | toggle | off | restore | info] for the first
 * symmetry (also //sym1), //sym2 [set [type] [even | odd] | placeaim [type] [even | odd] | type &lt;type&gt;
 * | add &lt;plane&gt; | remove [plane] | swap | toggle | off | restore | info] for
 * the second, which copies the first one too. The range is that of the first symmetry, for both; //sym toggle pauses
 * both, //sym2 toggle the second one only.
 * <p>
 * An even point is between blocks: the blocks go in pairs around it. //sym take, run by the buttons of a share,
 * gives a shared symmetry to the player who clicks.
 */
public final class SymmetryCommand implements TabExecutor {

    private static final String USAGE = "//sym [set [type] [even | odd] | placeaim [type] [even | odd] | type <type> "
            + "| add <plane> | remove [plane] | range <blocks | max> | swap | share [player] | toggle | off | restore | info]";
    private static final String USAGE_SECOND = "//sym2 [set [type] [even | odd] | placeaim [type] [even | odd] "
            + "| type <type> | add <plane> | remove [plane] | swap | toggle | off | restore | info]";

    private final SymmetrySessions sessions;
    // //sym2: acts on the second symmetry
    private final boolean second;

    public SymmetryCommand(SymmetrySessions sessions, boolean second) {
        this.sessions = sessions;
        this.second = second;
    }

    /**
     * The type and the parity given to set and placeaim, null when not given.
     */
    private record Options(@Nullable SymmetryType type, @Nullable Boolean even) {
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
                case "set", "s" -> setSecond(player, args, false);
                case "placeaim", "pa" -> setSecond(player, args, true);
                case "type", "t" -> typeSecond(player, args);
                case "add" -> addPlane(player, args);
                case "remove" -> {
                    if (args.length > 1) {
                        removePlane(player, args);
                    } else {
                        offSecond(player);
                    }
                }
                case "swap" -> swap(player);
                case "toggle" -> toggleSecond(player);
                case "off", "clear" -> offSecond(player);
                case "restore" -> restoreSecond(player);
                case "info", "i" -> infoSecond(player);
                default -> Messages.send(player, "usage", "usage", USAGE_SECOND);
            }
            return true;
        }
        switch (sub) {
            case "set", "s" -> set(player, args, false);
            case "placeaim", "pa" -> set(player, args, true);
            case "type", "t" -> type(player, args);
            case "add" -> addPlane(player, args);
            case "remove" -> {
                if (args.length > 1) {
                    removePlane(player, args);
                } else {
                    off(player);
                }
            }
            case "range", "r" -> range(player, args);
            case "swap" -> swap(player);
            case "share" -> share(player, args);
            case "take" -> take(player, args);
            case "toggle" -> toggle(player);
            case "off", "clear" -> off(player);
            case "restore" -> restore(player);
            case "info", "i" -> info(player);
            default -> Messages.send(player, "usage", "usage", USAGE);
        }
        return true;
    }

    /**
     * //sym set [type] [even | odd]: puts the point at the player's feet, with the given type and parity, else the
     * previous ones. //sym placeaim [type] [even | odd]: the same, on the block the player is looking at.
     */
    private void set(Player player, String[] args, boolean aim) {
        Options options = options(player, args);
        if (options == null) {
            return;
        }
        Symmetry previous = sessions.get(player.getUniqueId());
        SymmetryType type = options.type() != null ? options.type()
                : previous != null ? previous.type() : sessions.config().defaultType();
        boolean even = options.even() != null ? options.even() : previous != null && previous.even();
        BlockVector3 center = point(player, aim, even);
        if (center == null) {
            return;
        }
        if (setFirst(player, player.getWorld().getName(), new Symmetry.Second(center, even, type))) {
            Symmetry symmetry = sessions.get(player.getUniqueId());
            Messages.send(player, "symmetry.set", "type", type.id(), "position", position(center, even),
                    "copies", symmetry.copyCount(), "range", symmetry.range(sessions.config().maxRange()));
        }
    }

    /**
     * Puts the first symmetry, keeping the range and the second symmetry when it is in the same world.
     *
     * @return whether it was put
     */
    private boolean setFirst(Player player, String world, Symmetry.Second first) {
        Symmetry previous = sessions.get(player.getUniqueId());
        // The second symmetry stays when the first moves in its world
        Symmetry.Second second = previous != null && previous.world().equals(world) ? previous.second() : null;
        if (!combinable(player, first.type(), second)) {
            return false;
        }
        sessions.set(player.getUniqueId(), new Symmetry(world, first.center(), first.even(), first.type(),
                previous != null ? previous.range() : 0, true, second));
        return true;
    }

    /**
     * //sym2 set [type] [even | odd]: puts the second point at the player's feet, with the given type and parity,
     * else the previous ones. It copies the blocks and the copies of the first symmetry.
     * //sym2 placeaim [type] [even | odd]: on the aimed block.
     */
    private void setSecond(Player player, String[] args, boolean aim) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        if (!symmetry.world().equals(player.getWorld().getName())) {
            Messages.send(player, "symmetry.other-world", "world", symmetry.world());
            return;
        }
        Options options = options(player, args);
        if (options == null) {
            return;
        }
        Symmetry.Second previous = symmetry.second();
        SymmetryType type = options.type() != null ? options.type()
                : previous != null ? previous.type() : sessions.config().defaultType();
        boolean even = options.even() != null ? options.even() : previous != null ? previous.even() : symmetry.even();
        BlockVector3 center = point(player, aim, even);
        if (center == null) {
            return;
        }
        Symmetry.Second second = new Symmetry.Second(center, even, type);
        if (!combinable(player, symmetry.type(), second)) {
            return;
        }
        Symmetry updated = symmetry.withSecond(second);
        sessions.set(player.getUniqueId(), updated);
        Messages.send(player, "symmetry.added", "type", type.id(), "position", position(center, even),
                "copies", updated.copyCount());
    }

    /**
     * @return the type and the parity after the subcommand, in any order, or null after telling the player
     */
    @Nullable
    private static Options options(Player player, String[] args) {
        SymmetryType type = null;
        Boolean even = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("even") || arg.equals("odd")) {
                even = arg.equals("even");
            } else {
                type = type(player, arg);
                if (type == null) {
                    return null;
                }
            }
        }
        return new Options(type, even);
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
            applyType(player, symmetry, type);
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
        if (type != null) {
            applyType(player, symmetry, type);
        }
    }

    /**
     * Changes the type of the symmetry of this command (the second one for //sym2), without moving its point.
     *
     * @return whether it was changed, false after telling the player that the two symmetries make too many copies
     */
    private boolean applyType(Player player, Symmetry symmetry, SymmetryType type) {
        if (second) {
            Symmetry.Second changed = symmetry.second().withType(type);
            if (!combinable(player, symmetry.type(), changed)) {
                return false;
            }
            Symmetry updated = symmetry.withSecond(changed);
            sessions.set(player.getUniqueId(), updated);
            Messages.send(player, "symmetry.type-second", "type", type.id(), "copies", updated.copyCount());
            return true;
        }
        if (!combinable(player, type, symmetry.second())) {
            return false;
        }
        Symmetry updated = symmetry.withType(type);
        sessions.set(player.getUniqueId(), updated);
        Messages.send(player, "symmetry.type", "type", type.id(), "copies", updated.copyCount());
        return true;
    }

    /**
     * //sym add &lt;plane&gt;: adds a plane to the symmetry, which becomes the type with all its planes
     * (star2d + plany is star3d).
     */
    private void addPlane(Player player, String[] args) {
        Symmetry symmetry = second ? existingSecond(player) : existing(player);
        if (symmetry == null) {
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", command() + " add <plane>");
            return;
        }
        SymmetryType plane = plane(player, args[1]);
        if (plane == null) {
            return;
        }
        SymmetryType current = currentType(symmetry);
        if (current.hasPlane(plane)) {
            Messages.send(player, "symmetry.plane-present", "type", current.id(), "plane", plane.id());
            return;
        }
        SymmetryType type = current.withPlane(plane);
        if (type == null) {
            Messages.send(player, "symmetry.no-type-with-plane", "type", current.id(), "plane", plane.id());
            return;
        }
        applyType(player, symmetry, type);
    }

    /**
     * //sym remove &lt;plane&gt;: removes a plane from the symmetry, which becomes the type with the planes left
     * (star3d without plany is star2d). Without its last plane, the symmetry is removed.
     */
    private void removePlane(Player player, String[] args) {
        Symmetry symmetry = second ? existingSecond(player) : existing(player);
        if (symmetry == null) {
            return;
        }
        SymmetryType plane = plane(player, args[1]);
        if (plane == null) {
            return;
        }
        SymmetryType current = currentType(symmetry);
        if (!current.hasPlane(plane)) {
            Messages.send(player, "symmetry.plane-absent", "type", current.id(), "plane", plane.id());
            return;
        }
        if (current.planes().equals(List.of(plane))) {
            removeLastPlane(player, symmetry);
            return;
        }
        SymmetryType type = current.withoutPlane(plane);
        if (type == null) {
            List<String> left = current.planes().stream().filter(p -> p != plane).map(SymmetryType::id).toList();
            Messages.send(player, "symmetry.no-type-without-plane", "type", current.id(), "plane", plane.id(),
                    "planes", String.join(", ", left));
            return;
        }
        if (applyType(player, symmetry, type)) {
            // The planes that cannot stay without this one (star3d without planz loses planx too)
            List<String> dropped = current.planes().stream().filter(p -> p != plane && !type.hasPlane(p))
                    .map(SymmetryType::id).toList();
            if (!dropped.isEmpty()) {
                Messages.send(player, "symmetry.planes-dropped", "planes", String.join(", ", dropped),
                        "plane", plane.id());
            }
        }
    }

    /**
     * Removes the symmetry that lost its last plane, like //sym off or //sym2 off. The first one, when there is a
     * second, gives its place to it and is kept for //sym2 restore.
     */
    private void removeLastPlane(Player player, Symmetry symmetry) {
        if (second) {
            offSecond(player);
        } else if (symmetry.second() == null) {
            off(player);
        } else {
            Symmetry promoted = symmetry.withFirst(symmetry.second()).withSecond(null)
                    .withEnabled(symmetry.enabled() && symmetry.second().enabled());
            sessions.keepSecond(player.getUniqueId(), symmetry.world(), symmetry.first());
            sessions.set(player.getUniqueId(), promoted);
            Messages.send(player, "symmetry.first-removed", "type", promoted.type().id());
        }
    }

    private SymmetryType currentType(Symmetry symmetry) {
        return second ? symmetry.second().type() : symmetry.type();
    }

    private String command() {
        return second ? "//sym2" : "//sym";
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

    /**
     * //sym swap: the first symmetry becomes the second and the second the first.
     */
    private void swap(Player player) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry != null) {
            Symmetry swapped = symmetry.swapped();
            sessions.set(player.getUniqueId(), swapped);
            Messages.send(player, "symmetry.swapped", "first", swapped.type().id(), "second", swapped.second().type().id());
        }
    }

    /**
     * //sym share [player]: shows the player's symmetries in the chat of everyone who can use them, or of one player,
     * with a button to take each of them.
     */
    private void share(Player player, String[] args) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        List<Player> receivers = new ArrayList<>();
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Messages.send(player, "symmetry.unknown-player", "player", args[1]);
                return;
            }
            receivers.add(target);
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(SymmetrySessions.PERMISSION)) {
                    receivers.add(online);
                }
            }
        }
        int id = sessions.share(player.getName(), symmetry);
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.get("symmetry.share", "player", player.getName(), "world", symmetry.world()));
        lines.add(shareLine(id, 1, symmetry.first()));
        if (symmetry.second() != null) {
            lines.add(shareLine(id, 2, symmetry.second()));
        }
        for (Player receiver : receivers) {
            for (Component line : lines) {
                if (line != null) {
                    receiver.sendMessage(line);
                }
            }
        }
        if (!receivers.contains(player)) {
            Messages.send(player, "symmetry.shared", "player", receivers.get(0).getName());
        }
    }

    /**
     * A shared symmetry, with a button to take it as one's first symmetry and one as one's second.
     */
    private static Component shareLine(int id, int number, Symmetry.Second shared) {
        Component line = Messages.plain("symmetry.share-line", "number", number, "type", shared.type().id(),
                "position", position(shared.center(), shared.even()));
        return line.append(Component.space())
                .append(button("symmetry.share-take-first", "//sym take " + id + " " + number + " 1"))
                .append(Component.space())
                .append(button("symmetry.share-take-second", "//sym take " + id + " " + number + " 2"));
    }

    private static Component button(String key, String command) {
        return Messages.plain(key)
                .clickEvent(ClickEvent.runCommand(command));
    }

    /**
     * //sym take &lt;share&gt; &lt;1 | 2&gt; &lt;1 | 2&gt;: takes the first or the second symmetry of a share as one's
     * first or second symmetry. Run by the buttons of //sym share.
     */
    private void take(Player player, String[] args) {
        if (args.length < 4) {
            Messages.send(player, "usage", "usage", "//sym take <share> <1 | 2> <1 | 2>");
            return;
        }
        int id;
        int number;
        int slot;
        try {
            id = Integer.parseInt(args[1]);
            number = Integer.parseInt(args[2]);
            slot = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            Messages.send(player, "usage", "usage", "//sym take <share> <1 | 2> <1 | 2>");
            return;
        }
        SymmetrySessions.Share share = sessions.share(id);
        Symmetry.Second shared = share == null ? null
                : number == 1 ? share.symmetry().first() : number == 2 ? share.symmetry().second() : null;
        if (shared != null) {
            shared = shared.withEnabled(true);
        }
        if (shared == null || (slot != 1 && slot != 2)) {
            Messages.send(player, "symmetry.share-expired");
            return;
        }
        String world = share.symmetry().world();
        if (slot == 1) {
            if (!setFirst(player, world, shared)) {
                return;
            }
        } else {
            Symmetry symmetry = existing(player);
            if (symmetry == null) {
                return;
            }
            if (!symmetry.world().equals(world)) {
                Messages.send(player, "symmetry.other-world", "world", symmetry.world());
                return;
            }
            if (!combinable(player, symmetry.type(), shared)) {
                return;
            }
            sessions.set(player.getUniqueId(), symmetry.withSecond(shared));
        }
        Messages.send(player, "symmetry.taken", "number", number, "player", share.owner(), "type", shared.type().id(),
                "position", position(shared.center(), shared.even()), "world", world, "slot", slot);
    }

    private void toggle(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null) {
            sessions.set(player.getUniqueId(), symmetry.withEnabled(!symmetry.enabled()));
            Messages.send(player, symmetry.enabled() ? "symmetry.paused" : "symmetry.resumed");
        }
    }

    /**
     * //sym2 toggle: pauses or resumes the second symmetry only, keeping it.
     */
    private void toggleSecond(Player player) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry != null) {
            boolean enabled = !symmetry.second().enabled();
            sessions.set(player.getUniqueId(), symmetry.withSecond(symmetry.second().withEnabled(enabled)));
            Messages.send(player, enabled ? "symmetry.second-resumed" : "symmetry.second-paused");
        }
    }

    /**
     * //sym off: removes both symmetries, kept for //sym restore.
     */
    private void off(Player player) {
        Messages.send(player, sessions.off(player.getUniqueId()) != null ? "symmetry.removed" : "symmetry.none");
    }

    /**
     * //sym restore: puts back the symmetries removed with //sym off, enabled. The current ones, if any, can be
     * restored in turn.
     */
    private void restore(Player player) {
        Symmetry restored = sessions.restore(player.getUniqueId());
        if (restored == null) {
            Messages.send(player, "symmetry.nothing-to-restore");
            return;
        }
        Messages.send(player, "symmetry.restored", "type", restored.type().id(),
                "position", position(restored.center(), restored.even()), "world", restored.world(),
                "copies", restored.copyCount());
    }

    /**
     * //sym2 off: removes the second symmetry, keeping the first.
     */
    private void offSecond(Player player) {
        Symmetry symmetry = existingSecond(player);
        if (symmetry != null) {
            sessions.keepSecond(player.getUniqueId(), symmetry.world(), symmetry.second());
            sessions.set(player.getUniqueId(), symmetry.withSecond(null));
            Messages.send(player, "symmetry.second-removed");
        }
    }

    /**
     * //sym2 restore: puts back the second symmetry removed with //sym2 off, enabled. The current second one, if
     * any, can be restored in turn.
     */
    private void restoreSecond(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry == null) {
            return;
        }
        SymmetrySessions.RemovedSecond removed = sessions.restorableSecond(player.getUniqueId());
        if (removed == null) {
            Messages.send(player, "symmetry.nothing-to-restore-second");
            return;
        }
        if (!removed.world().equals(symmetry.world())) {
            Messages.send(player, "symmetry.other-world", "world", symmetry.world());
            return;
        }
        Symmetry.Second second = removed.second().withEnabled(true);
        if (!combinable(player, symmetry.type(), second)) {
            return;
        }
        sessions.takeSecond(player.getUniqueId());
        if (symmetry.second() != null) {
            sessions.keepSecond(player.getUniqueId(), symmetry.world(), symmetry.second());
        }
        Symmetry updated = symmetry.withSecond(second);
        sessions.set(player.getUniqueId(), updated);
        Messages.send(player, "symmetry.second-restored", "type", second.type().id(),
                "position", position(second.center(), second.even()), "copies", updated.copyCount());
    }

    private void info(Player player) {
        Symmetry symmetry = existing(player);
        if (symmetry != null) {
            Messages.send(player, symmetry.enabled() ? "symmetry.info" : "symmetry.info-paused", "type", symmetry.type().id(),
                    "position", position(symmetry.center(), symmetry.even()), "world", symmetry.world(),
                    "copies", symmetry.copyCount(), "range", symmetry.range(sessions.config().maxRange()));
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
        Messages.send(player, second.enabled() ? "symmetry.info-second" : "symmetry.info-second-paused",
                "type", second.type().id(),
                "position", position(second.center(), second.even()), "copies", symmetry.copyCount());
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
    private static SymmetryType plane(Player player, String name) {
        SymmetryType plane = SymmetryType.fromId(name);
        if (plane == null || !plane.isPlane()) {
            Messages.send(player, "symmetry.unknown-plane", "plane", name, "planes",
                    String.join(", ", planeIds().toList()));
            return null;
        }
        return plane;
    }

    private static Stream<String> planeIds() {
        return SymmetryType.planeTypes().stream().map(SymmetryType::id);
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

    /**
     * The block of the point: the player's feet, or the aimed block. An even point is put on the edges of blocks
     * nearest to the player, or to the aimed spot: aiming at a face puts it on that face.
     *
     * @return the block of the point, or null after telling the player that they aim at nothing
     */
    @Nullable
    private BlockVector3 point(Player player, boolean aim, boolean even) {
        if (!aim) {
            Location feet = player.getLocation();
            return even ? corner(feet.toVector())
                    : BlockVector3.at(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
        }
        int range = sessions.config().aimRange();
        if (even) {
            RayTraceResult hit = player.rayTraceBlocks(range);
            if (hit != null && hit.getHitBlock() != null) {
                return corner(hit.getHitPosition());
            }
        } else {
            Block block = player.getTargetBlockExact(range);
            if (block != null) {
                return BlockVector3.at(block.getX(), block.getY(), block.getZ());
            }
        }
        Messages.send(player, "symmetry.no-aimed-block", "range", range);
        return null;
    }

    /**
     * @return the block whose +x, +y, +z corner is the nearest one to the position
     */
    private static BlockVector3 corner(Vector position) {
        return BlockVector3.at(Math.round(position.getX()) - 1, Math.round(position.getY()) - 1,
                Math.round(position.getZ()) - 1);
    }

    /**
     * @return the position of the point, between blocks when it is even
     */
    public static String position(BlockVector3 center, boolean even) {
        String position = center.x() + ", " + center.y() + ", " + center.z();
        return even ? Messages.raw("symmetry.even-position").replace("{position}", position) : position;
    }

    private static Stream<String> ids() {
        return Stream.of(SymmetryType.values()).map(SymmetryType::id);
    }

    /**
     * @return the planes the symmetry does not have, for add, or those it has, for remove
     */
    private Stream<String> planeCompletions(CommandSender sender, boolean add) {
        Symmetry symmetry = sender instanceof Player player ? sessions.get(player.getUniqueId()) : null;
        if (symmetry == null || (second && symmetry.second() == null)) {
            return planeIds();
        }
        SymmetryType current = currentType(symmetry);
        return SymmetryType.planeTypes().stream().filter(plane -> current.hasPlane(plane) != add)
                .map(SymmetryType::id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            Stream<String> subs = second
                    ? Stream.of("set", "placeaim", "type", "add", "remove", "swap", "toggle", "off", "restore", "info")
                    : Stream.of("set", "placeaim", "type", "add", "remove", "range", "swap", "share", "toggle", "off",
                    "restore", "info");
            return subs.filter(s -> s.startsWith(last)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "set", "s", "placeaim", "pa" -> Stream.concat(args.length <= 3 ? ids() : Stream.empty(),
                    Stream.of("even", "odd")).filter(id -> id.startsWith(last)).toList();
            case "type", "t" -> args.length == 2 ? ids().filter(id -> id.startsWith(last)).toList() : List.of();
            case "add", "remove" -> args.length == 2 ? planeCompletions(sender, sub.equals("add"))
                    .filter(id -> id.startsWith(last)).toList() : List.of();
            case "range", "r" -> second || args.length > 2 ? List.of()
                    : Stream.of("16", "32", "64", "max").filter(s -> s.startsWith(last)).toList();
            case "share" -> second || args.length > 2 ? List.of() : Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(last)).toList();
            default -> List.of();
        };
    }
}

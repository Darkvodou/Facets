package fr.darkvodou.facets.fill;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.UnknownDirectionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import fr.darkvodou.facets.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Fills the air around the player without going through the holes up to the tolerance (in blocks), like a cave
 * with a hole in its wall. Named after WorldEdit's fills:
 * <ul>
 *     <li>//fuzzyfill &lt;pattern&gt; &lt;radius&gt; [tolerance] [direction]: the layer of the player's feet, across
 *     the direction (down by default)</li>
 *     <li>//fuzzyfillr &lt;pattern&gt; &lt;radius&gt; [tolerance] [direction]: that layer and what is past it in the
 *     direction</li>
 *     <li>//fuzzyfills &lt;pattern&gt; &lt;radius&gt; [tolerance]: the whole sphere</li>
 * </ul>
 */
public final class FuzzyFillCommand implements TabExecutor {

    private static final int DEFAULT_TOLERANCE = 1;
    private static final List<String> TOLERANCES = List.of("0", "1", "2", "3");
    // The directions of WorldEdit's //fill
    private static final List<String> DIRECTIONS = List.of("down", "up", "north", "south", "east", "west",
            "me", "forward", "back", "left", "right");

    /**
     * Which blocks the fill may use, from the player's feet and the direction.
     */
    private enum Mode {
        LAYER, RECURSIVE, SPHERE;

        static Mode of(Command command) {
            return switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "/fuzzyfill" -> LAYER;
                case "/fuzzyfillr" -> RECURSIVE;
                default -> SPHERE;
            };
        }

        boolean takesDirection() {
            return this != SPHERE;
        }

        boolean allows(BlockVector3 position, BlockVector3 start, BlockVector3 direction) {
            // How far the position is past the start in the direction
            BlockVector3 offset = position.subtract(start);
            int past = offset.x() * direction.x() + offset.y() * direction.y() + offset.z() * direction.z();
            return switch (this) {
                case LAYER -> past == 0;
                case RECURSIVE -> past >= 0;
                case SPHERE -> true;
            };
        }
    }

    private final Plugin plugin;

    public FuzzyFillCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        Mode mode = Mode.of(command);
        if (args.length < 2 || args.length > (mode.takesDirection() ? 4 : 3)) {
            Messages.send(player, "usage", "usage", "/" + label + " <pattern> <radius> [tolerance]"
                    + (mode.takesDirection() ? " [direction]" : ""));
            return true;
        }
        // Read on each use, so that /facets reload applies it
        int maxRadius = Math.max(1, plugin.getConfig().getInt("fuzzyfill.max-radius", 64));
        Integer radius = number(player, args[1]);
        if (radius == null) {
            return true;
        }
        if (radius < 1 || radius > maxRadius) {
            Messages.send(player, "fuzzyfill.radius-range", "max", maxRadius);
            return true;
        }
        // The tolerance can be left out before the direction
        int next = 2;
        Integer tolerance = DEFAULT_TOLERANCE;
        if (args.length > next && (args.length == 4 || !mode.takesDirection() || isNumber(args[next]))) {
            tolerance = number(player, args[next++]);
            if (tolerance == null) {
                return true;
            }
        }
        if (tolerance < 0 || tolerance > radius) {
            Messages.send(player, "fuzzyfill.tolerance-range", "max", radius);
            return true;
        }

        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        BlockVector3 direction = BlockVector3.UNIT_MINUS_Y;
        if (args.length > next) {
            try {
                direction = WorldEdit.getInstance().getDirection(actor, args[next].toLowerCase(Locale.ROOT));
            } catch (UnknownDirectionException e) {
                Messages.send(player, "fuzzyfill.invalid-direction", "value", args[next]);
                return true;
            }
        }
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        World world = actor.getWorld();
        Pattern pattern;
        try {
            ParserContext context = new ParserContext();
            context.setActor(actor);
            context.setWorld(world);
            context.setSession(session);
            context.setRestricted(true);
            pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(args[0], context);
        } catch (InputParseException e) {
            Messages.send(player, "fuzzyfill.invalid-pattern", "message", e.getMessage());
            return true;
        }

        Location location = player.getLocation();
        BlockVector3 start = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        int r = radius;
        int t = tolerance;
        BlockVector3 d = direction;
        String name = "/" + command.getName().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EditSession editSession = session.createEditSession(actor, name);
            int minY = world.getMinY();
            int maxY = world.getMaxY();
            try {
                List<BlockVector3> blocks = FuzzyFill.blocks(start, r, t, mode == Mode.LAYER ? d : null,
                        position -> position.y() >= minY && position.y() <= maxY && mode.allows(position, start, d)
                                && editSession.getBlock(position).getBlockType().getMaterial().isAir());
                if (blocks.isEmpty()) {
                    Messages.send(player, editSession.getBlock(start).getBlockType().getMaterial().isAir()
                            ? "fuzzyfill.no-room" : "fuzzyfill.not-air", "tolerance", t);
                    return;
                }
                for (BlockVector3 block : blocks) {
                    editSession.setBlock(block, pattern);
                }
            } catch (WorldEditException e) {
                Messages.send(player, "fuzzyfill.failed", "message", e.getMessage());
            } catch (RuntimeException e) {
                Messages.send(player, "fuzzyfill.failed", "message", e);
                plugin.getLogger().log(java.util.logging.Level.WARNING, name + " failed", e);
            } finally {
                editSession.close();
                session.remember(editSession);
            }
            if (editSession.getBlockChangeCount() > 0) {
                Messages.send(player, "fuzzyfill.done", "count", editSession.getBlockChangeCount());
            }
        });
        return true;
    }

    private static boolean isNumber(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Integer number(Player player, String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Messages.send(player, "fuzzyfill.invalid-number", "value", input);
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1];
        boolean direction = Mode.of(command).takesDirection();
        return switch (args.length) {
            case 1 -> patternSuggestions(sender, last);
            case 2 -> filter(List.of("10", "20", "32", "48"), last);
            case 3 -> filter(direction ? Stream.concat(TOLERANCES.stream(), DIRECTIONS.stream()).toList() : TOLERANCES,
                    last);
            case 4 -> direction && isNumber(args[2]) ? filter(DIRECTIONS, last) : List.of();
            default -> List.of();
        };
    }

    private static List<String> patternSuggestions(CommandSender sender, String input) {
        try {
            ParserContext context = new ParserContext();
            if (sender instanceof Player player) {
                com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
                context.setActor(actor);
                context.setWorld(actor.getWorld());
                context.setSession(WorldEdit.getInstance().getSessionManager().get(actor));
            }
            return WorldEdit.getInstance().getPatternFactory().getSuggestions(input, context);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}

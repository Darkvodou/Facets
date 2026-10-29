package fr.darkvodou.facets.loft;

import com.fastasyncworldedit.core.configuration.Caption;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.util.formatting.component.CommandListBox;
import com.sk89q.worldedit.util.formatting.component.InvalidComponentException;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.world.World;
import fr.darkvodou.facets.Messages;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * //loft [smooth | poly | facet | set | viz]. Also answers //sel loft, which WorldEdit does not know,
 * and adds loft to the list of //sel list.
 */
public final class LoftCommand implements TabExecutor, Listener {

    // //sel and its aliases (/;, //desel, //deselect), followed by loft or list
    private static final java.util.regex.Pattern SEL = java.util.regex.Pattern.compile(
            "^/(/sel|/select|/desel|/deselect|;)\\s+(loft|loftp|loftpoly|loftf|loftfacet|list)(\\s.*)?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final String SEL_PERMISSION = "worldedit.analysis.sel";
    private static final java.util.regex.Pattern SEL_COMPLETION = java.util.regex.Pattern.compile(
            "^/(/sel|/select|/desel|/deselect|;) (\\S*)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final Flags SET_FLAGS = new Flags('o', "outline", 'c', "closed", 'd', "down", 'h', "horizontal",
            'n', "net", 'u', "uniform");
    private static final String PERMISSION = "facets.loft";
    private static final String USAGE = "//loft [smooth | poly | facet | set <pattern> [count] [-o -c -d -h -n -u] | viz]";
    private static final String SET_USAGE = "//loft set <pattern> [count] [-o -c -d -h -n -u]";

    private final Plugin plugin;
    private final LoftVisualizer visualizer;
    private volatile LoftConfig config;

    public LoftCommand(Plugin plugin, LoftVisualizer visualizer, LoftConfig config) {
        this.plugin = plugin;
        this.visualizer = visualizer;
        this.config = config;
    }

    public void setConfig(LoftConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        java.util.regex.Matcher matcher = SEL.matcher(event.getMessage().trim());
        if (!matcher.matches()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String choice = matcher.group(2).toLowerCase(Locale.ROOT);
        String permission = choice.equals("list") ? SEL_PERMISSION : PERMISSION;
        if (!player.hasPermission(permission)) {
            Messages.send(player, "no-permission", "permission", permission);
        } else if (!choice.equals("list")) {
            select(player, LoftShape.fromSelection(choice));
        } else {
            listSelectors(player);
        }
    }

    // FAWE completes //sel asynchronously
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        List<String> completions = withLoft(event.getSender(), event.getBuffer(), event.getCompletions());
        if (completions != null) {
            event.setCompletions(completions);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        List<String> completions = withLoft(event.getSender(), event.getBuffer(), event.getCompletions());
        if (completions != null) {
            event.setCompletions(completions);
        }
    }

    /**
     * @return the completions with the loft selections added when completing the selector of //sel, else null
     */
    private static List<String> withLoft(CommandSender sender, String buffer, List<String> completions) {
        java.util.regex.Matcher matcher = SEL_COMPLETION.matcher(buffer);
        if (!matcher.matches() || !sender.hasPermission(PERMISSION)) {
            return null;
        }
        String typed = matcher.group(2).toLowerCase(Locale.ROOT);
        List<String> added = Stream.of(LoftShape.values()).map(LoftShape::selection)
                .filter(choice -> choice.startsWith(typed) && !completions.contains(choice))
                .toList();
        if (added.isEmpty()) {
            return null;
        }
        List<String> withLoft = new ArrayList<>(completions);
        withLoft.addAll(added);
        return withLoft;
    }

    /**
     * Same box as FAWE's //sel list, with the loft selection added.
     */
    private static void listSelectors(Player player) {
        CommandListBox box = new CommandListBox("Selection modes", null, null);
        box.setHidingHelp(true);
        TextComponentProducer contents = box.getContents();
        contents.append(SubtleFormat.wrap("Select one of the modes below:")).newline();

        box.appendCommand("cuboid", Caption.of("worldedit.select.cuboid.description"), "//sel cuboid");
        box.appendCommand("extend", Caption.of("worldedit.select.extend.description"), "//sel extend");
        box.appendCommand("poly", Caption.of("worldedit.select.poly.description"), "//sel poly");
        box.appendCommand("ellipsoid", Caption.of("worldedit.select.ellipsoid.description"), "//sel ellipsoid");
        box.appendCommand("sphere", Caption.of("worldedit.select.sphere.description"), "//sel sphere");
        box.appendCommand("cyl", Caption.of("worldedit.select.cyl.description"), "//sel cyl");
        box.appendCommand("convex", Caption.of("worldedit.select.convex.description"), "//sel convex");
        box.appendCommand("polyhedral", Caption.of("fawe.selection.sel.polyhedral"), "//sel polyhedral");
        box.appendCommand("fuzzy[=<mask>]", Caption.of("fawe.selection.sel.fuzzy-instruction"), "//sel fuzzy[=<mask>]");
        if (player.hasPermission(PERMISSION)) {
            for (LoftShape shape : LoftShape.values()) {
                box.appendCommand(shape.selection(), Messages.worldEdit(shape.message("loft.description")),
                        "//sel " + shape.selection());
            }
        }
        box.setComponentsPerPage(box.getComponentsSize());

        try {
            BukkitAdapter.adapt(player).print(box.create(1));
        } catch (InvalidComponentException e) {
            Messages.send(player, "loft.failed", "message", e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "smooth", "poly", "facet" -> select(player, LoftShape.fromSubcommand(sub));
            case "set", "s" -> set(player, args);
            case "viz", "show" -> Messages.send(player, visualizer.toggle(player.getUniqueId())
                    ? "loft.particles-on" : "loft.particles-off");
            default -> Messages.send(player, "usage", "usage", USAGE);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("set", "smooth", "poly", "facet", "viz"), args[0]);
        }
        if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("s")) {
            return completeSet(sender, args);
        }
        return List.of();
    }

    /**
     * //loft set &lt;pattern&gt; [count] [flags]: flags can come anywhere and be combined (-nu).
     */
    private static List<String> completeSet(CommandSender sender, String[] args) {
        String last = args[args.length - 1];
        Set<Character> used = new HashSet<>();
        int positional = 0;
        for (int i = 1; i < args.length - 1; i++) {
            String letters = SET_FLAGS.parse(args[i]);
            if (letters != null) {
                letters.chars().forEach(c -> used.add((char) c));
            } else {
                positional++;
            }
        }
        if (last.startsWith("-")) {
            return SET_FLAGS.complete(last, used);
        }
        List<String> options = new ArrayList<>();
        if (positional == 0) {
            options.addAll(patternSuggestions(sender, last));
        } else if (positional == 1) {
            options.addAll(filter(List.of("1", "3", "5", "8", "10"), last));
        }
        if (positional > 0) {
            options.addAll(filter(SET_FLAGS.all(used), last));
        }
        return options;
    }

    private static List<String> patternSuggestions(CommandSender sender, String input) {
        try {
            ParserContext context = new ParserContext();
            if (sender instanceof Player player) {
                com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
                context.setActor(actor);
                context.setWorld(actor.getWorld());
                context.setSession(LoftSessions.session(player));
            }
            return WorldEdit.getInstance().getPatternFactory().getSuggestions(input, context);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    /**
     * Switches to a loft selection, keeping the frames when switching between loft shapes.
     */
    private static void select(Player player, LoftShape shape) {
        LocalSession session = LoftSessions.session(player);
        World world = BukkitAdapter.adapt(player.getWorld());
        RegionSelector current = session.getRegionSelector(world);
        if (current instanceof LoftRegionSelector loft && loft.getLoft().getShape() == shape) {
            Messages.send(player, shape.message("loft.already-selected"));
            return;
        }
        session.setRegionSelector(world, LoftRegionSelector.from(world, shape, current));
        Messages.send(player, shape.message("loft.selected"));
    }

    /**
     * //loft set &lt;pattern&gt; [count] [-o -c -d -h -n -u]: low poly if the selection is a loft poly
     */
    private void set(Player player, String[] args) {
        String patternInput = null;
        int count = 0;
        Set<Character> flags = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String letters = SET_FLAGS.parse(arg);
            if (letters != null) {
                for (char flag : letters.toCharArray()) {
                    flags.add(flag);
                }
            } else if (patternInput == null) {
                patternInput = arg;
            } else {
                try {
                    count = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    Messages.send(player, "loft.invalid-count", "count", arg);
                    return;
                }
                if (count < 1) {
                    Messages.send(player, "loft.count-too-low");
                    return;
                }
            }
        }
        if (patternInput == null) {
            Messages.send(player, "usage", "usage", SET_USAGE);
            return;
        }
        for (char flag : flags) {
            if (!SET_FLAGS.isKnown(flag)) {
                Messages.send(player, "loft.invalid-flag", "flag", "-" + flag);
                return;
            }
        }

        LoftRegionSelector selector = LoftSessions.selector(player);
        if (selector == null) {
            Messages.send(player, "loft.no-selection");
            return;
        }
        if (!selector.isDefined()) {
            Messages.send(player, "loft.incomplete");
            return;
        }

        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = LoftSessions.session(player);
        World world = actor.getWorld();
        Pattern pattern;
        try {
            ParserContext context = new ParserContext();
            context.setActor(actor);
            context.setWorld(world);
            context.setSession(session);
            context.setRestricted(true);
            pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(patternInput, context);
        } catch (InputParseException e) {
            Messages.send(player, "loft.invalid-pattern", "message", e.getMessage());
            return;
        }

        if (flags.contains('n') && count == 0) {
            count = config.defaultNetCount();
        }
        // Same shape as the selection, so that //replace, //cut... match what is set
        LoftOptions options = new LoftOptions(selector.getLoft().getShape(), flags.contains('c'), flags.contains('o'),
                count, flags.contains('h'), flags.contains('n'), flags.contains('u'), flags.contains('d'));
        List<List<BlockVector3>> frames = selector.getLoft().getFrames();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EditSession editSession = session.createEditSession(actor, "//loft set");
            try {
                Set<BlockVector3> blocks = new LoftSurface(frames, options).blocks(options);
                if (options.down()) {
                    blocks = stackDown(editSession, blocks, world.getMinY());
                }
                for (BlockVector3 block : blocks) {
                    editSession.setBlock(block, pattern);
                }
            } catch (WorldEditException e) {
                Messages.send(player, "loft.failed", "message", e.getMessage());
            } catch (RuntimeException e) {
                Messages.send(player, "loft.failed", "message", e);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "//loft set failed", e);
            } finally {
                editSession.close();
                session.remember(editSession);
            }
            Messages.send(player, "loft.done", "count", editSession.getBlockChangeCount());
        });
    }

    /**
     * Adds the air below each block, down to the first solid block.
     */
    private static Set<BlockVector3> stackDown(EditSession editSession, Set<BlockVector3> blocks, int minY) {
        Set<BlockVector3> stacked = new HashSet<>(blocks);
        for (BlockVector3 block : blocks) {
            for (int y = block.y() - 1; y >= minY; y--) {
                BlockVector3 below = block.withY(y);
                if (blocks.contains(below) || !editSession.getBlock(below).getBlockType().getMaterial().isAir()) {
                    break;
                }
                stacked.add(below);
            }
        }
        return stacked;
    }
}

package fr.darkvodou.facets.deform;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import fr.darkvodou.facets.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The arguments of //fdeform magnet and of its brush: numbers in a set order, then -m &lt;mask&gt; and -t anywhere.
 *
 * @param numbers the numbers given, in order
 * @param mask    the -m mask, null without it
 * @param trim    false with -t (or -trim)
 */
record MagnetOptions(List<Integer> numbers, @Nullable Mask mask, boolean trim) {

    static final int DEFAULT_FORCE = 50;

    /**
     * @param from       the first argument after the subcommand
     * @param maxNumbers how many numbers may be given
     * @param usage      the usage shown on a wrong argument
     * @return the options, or null after telling the player what is wrong
     */
    @Nullable
    static MagnetOptions parse(Player player, String[] args, int from, int maxNumbers, String usage) {
        List<Integer> numbers = new ArrayList<>();
        String maskInput = null;
        boolean trim = true;
        for (int i = from; i < args.length; i++) {
            if (isTrimFlag(args[i])) {
                trim = false;
            } else if (args[i].equalsIgnoreCase("-m")) {
                if (maskInput != null || i + 1 >= args.length) {
                    Messages.send(player, "usage", "usage", usage);
                    return null;
                }
                maskInput = args[++i];
            } else if (numbers.size() < maxNumbers) {
                try {
                    numbers.add(Integer.parseInt(args[i]));
                } catch (NumberFormatException e) {
                    Messages.send(player, "fdeform.invalid-number", "value", args[i]);
                    return null;
                }
            } else {
                Messages.send(player, "usage", "usage", usage);
                return null;
            }
        }
        Mask mask = null;
        if (maskInput != null) {
            try {
                mask = WorldEdit.getInstance().getMaskFactory().parseFromInput(maskInput, context(player, true));
            } catch (InputParseException e) {
                Messages.send(player, "fdeform.invalid-mask", "message", e.getMessage());
                return null;
            }
        }
        return new MagnetOptions(List.copyOf(numbers), mask, trim);
    }

    /**
     * The force from the given number, or null after telling the player it is out of range.
     */
    @Nullable
    static Integer force(Player player, @Nullable Integer force) {
        if (force == null) {
            return DEFAULT_FORCE;
        }
        if (force < 1 || force > 100) {
            Messages.send(player, "fdeform.force-range");
            return null;
        }
        return force;
    }

    /**
     * @param from        the first argument after the subcommand
     * @param suggestions the numbers suggested at each place
     */
    static List<String> complete(CommandSender sender, String[] args, int from, List<List<String>> suggestions) {
        String last = args[args.length - 1];
        if (args.length >= 2 && args[args.length - 2].equalsIgnoreCase("-m")) {
            return maskSuggestions(sender, last);
        }
        boolean hasMask = false;
        boolean hasTrim = false;
        int numbers = 0;
        for (int i = from; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("-m")) {
                hasMask = true;
                i++;
            } else if (isTrimFlag(args[i])) {
                hasTrim = true;
            } else {
                numbers++;
            }
        }
        List<String> options = new ArrayList<>();
        if (numbers < suggestions.size()) {
            options.addAll(suggestions.get(numbers));
        }
        if (!hasMask) {
            options.add("-m");
        }
        if (!hasTrim) {
            options.add("-t");
        }
        return options.stream().filter(o -> o.startsWith(last.toLowerCase(Locale.ROOT))).toList();
    }

    private static boolean isTrimFlag(String arg) {
        return arg.equalsIgnoreCase("-t") || arg.equalsIgnoreCase("-trim");
    }

    private static ParserContext context(Player player, boolean restricted) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(actor.getWorld());
        context.setExtent(actor.getWorld());
        context.setSession(WorldEdit.getInstance().getSessionManager().get(actor));
        context.setRestricted(restricted);
        return context;
    }

    private static List<String> maskSuggestions(CommandSender sender, String input) {
        try {
            ParserContext context = sender instanceof Player player ? context(player, false) : new ParserContext();
            return WorldEdit.getInstance().getMaskFactory().getSuggestions(input, context);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}

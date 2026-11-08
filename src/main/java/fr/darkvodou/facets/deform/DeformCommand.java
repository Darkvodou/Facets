package fr.darkvodou.facets.deform;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
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
import java.util.logging.Level;

/**
 * //fdeform magnet [force] [-m &lt;mask&gt;] [-t]: pulls the blocks of the selection toward the player, the closest ones
 * the most, the farthest ones not at all, as much near the edges of the selection as in its middle (the blocks just
 * past the edges are pulled in). The force goes from 1 to 100, where the first solid block goes 90% of the way to the
 * player; no holes and no folds at any force. With -m, like the source mask of FAWE's //move -m: only the blocks
 * matching the mask are pulled, replacing what is where they land, and where they leave, air is set. The deformation
 * stays in the selection (trim); with -t (or -trim), the blocks of the selection pulled out of it land there too,
 * without its air replacing what is around. The global mask (//gmask) chooses which blocks can be changed.
 */
public final class DeformCommand implements TabExecutor {

    private static final String USAGE = " magnet [force] [-m <mask>] [-t]";

    private final Plugin plugin;

    public DeformCommand(Plugin plugin) {
        this.plugin = plugin;
    }

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
        MagnetOptions options = MagnetOptions.parse(player, args, 1, 1, usage);
        if (options == null) {
            return true;
        }
        Integer force = MagnetOptions.force(player, options.numbers().isEmpty() ? null : options.numbers().get(0));
        if (force == null) {
            return true;
        }

        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        World world = actor.getWorld();
        Region region;
        try {
            region = session.getSelection(world).clone();
        } catch (IncompleteRegionException e) {
            Messages.send(player, "fdeform.no-selection");
            return true;
        }
        Location location = player.getLocation();
        // In block coordinates (a block at its center), between the feet and the head of the player
        Vector3 toward = Vector3.at(location.getX() - 0.5, location.getY() + 0.5, location.getZ() - 0.5);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EditSession editSession = session.createEditSession(actor, "/fdeform magnet");
            try {
                MagnetDeform.apply(editSession, region, toward, force, options.mask(), options.trim());
            } catch (WorldEditException e) {
                Messages.send(player, "fdeform.failed", "message", e.getMessage());
            } catch (RuntimeException e) {
                Messages.send(player, "fdeform.failed", "message", e);
                plugin.getLogger().log(Level.WARNING, "//fdeform magnet failed", e);
            } finally {
                editSession.close();
                session.remember(editSession);
            }
            Messages.send(player, editSession.getBlockChangeCount() > 0 ? "fdeform.done" : "fdeform.none",
                    "count", editSession.getBlockChangeCount());
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return "magnet".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("magnet") : List.of();
        }
        if (!args[0].equalsIgnoreCase("magnet")) {
            return List.of();
        }
        return MagnetOptions.complete(sender, args, 1, List.of(List.of("25", "50", "75", "100")));
    }
}

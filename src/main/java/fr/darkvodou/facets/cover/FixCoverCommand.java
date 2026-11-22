package fr.darkvodou.facets.cover;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.EllipsoidRegion;
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
import java.util.Random;
import java.util.logging.Level;

/**
 * //fixcover [radius] [-r] [-n -s -e -w -u -d]: makes the glow lichen, sculk veins, resin clumps, vines, pale moss carpets, ladders,
 * amethyst clusters and buds, shelves, trapdoors, banners, coral fans and buttons of the selection (or of the sphere
 * around the player) hold on the blocks next to them (see {@link Cover}). The blocks that could hold on several faces
 * are removed, or with -r turned to one of them at random. -n -s -e -w -u -d forbid holding on the block in that
 * direction. The global mask (//gmask) chooses which of them are fixed.
 */
public final class FixCoverCommand implements TabExecutor {

    private final Plugin plugin;

    public FixCoverCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        CoverOptions options = CoverOptions.parse(args, 0);
        if (options == null) {
            Messages.send(player, "usage", "usage", "/" + label + " [radius] " + CoverOptions.FLAGS);
            return true;
        }
        String radiusArg = options.radius();
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        World world = actor.getWorld();
        Region region;
        if (radiusArg != null) {
            int radius;
            try {
                radius = Integer.parseInt(radiusArg);
            } catch (NumberFormatException e) {
                Messages.send(player, "fixcover.invalid-number", "value", radiusArg);
                return true;
            }
            // Read on each use, so that /facets reload applies it
            int maxRadius = Math.max(1, plugin.getConfig().getInt("fixcover.max-radius", 64));
            if (radius < 1 || radius > maxRadius) {
                Messages.send(player, "fixcover.radius-range", "max", maxRadius);
                return true;
            }
            Location location = player.getLocation();
            region = new EllipsoidRegion(world,
                    BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                    Vector3.at(radius, radius, radius));
        } else {
            try {
                region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(world).clone();
            } catch (IncompleteRegionException e) {
                Messages.send(player, "fixcover.no-selection");
                return true;
            }
        }

        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        Random randomizer = options.randomizer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Made by the session, so it holds the global mask: the plants it rejects are left as they are
            EditSession editSession = session.createEditSession(actor, "/fixcover");
            try {
                CoverFixer.fix(editSession, region, randomizer, options.excluded());
            } catch (WorldEditException e) {
                Messages.send(player, "fixcover.failed", "message", e.getMessage());
            } catch (RuntimeException e) {
                Messages.send(player, "fixcover.failed", "message", e);
                plugin.getLogger().log(Level.WARNING, "//fixcover failed", e);
            } finally {
                editSession.close();
                session.remember(editSession);
            }
            Messages.send(player, editSession.getBlockChangeCount() > 0 ? "fixcover.done" : "fixcover.none",
                    "count", editSession.getBlockChangeCount());
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return CoverOptions.complete(args, 0, List.of());
    }
}

package fr.darkvodou.moretools.symmetry;

import com.fastasyncworldedit.core.configuration.Settings;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Copies what players with a symmetry change: FAWE edits through {@link SymmetryExtent}, and the blocks placed,
 * broken or written on by hand.
 */
public final class SymmetryListener implements Listener {

    private final Plugin plugin;
    private final SymmetrySessions sessions;

    public SymmetryListener(Plugin plugin, SymmetrySessions sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    /**
     * FAWE only keeps the extents of the plugins it is told about, in its allowed-plugins setting.
     */
    public static void allowExtent() {
        List<String> allowed = Settings.settings().EXTENT.ALLOWED_PLUGINS;
        String name = SymmetryExtent.class.getName();
        if (allowed.stream().noneMatch(entry -> name.toLowerCase(Locale.ROOT).contains(entry.toLowerCase(Locale.ROOT)))) {
            allowed.add(name);
        }
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        // Above the history so the copies are undone with the edit. Undo and redo write below it, so they are not copied.
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY || event.getWorld() == null) {
            return;
        }
        SymmetryConfig config = sessions.config();
        Actor actor = event.getActor();
        if (!config.worldEdit() || actor == null || !actor.isPlayer()) {
            return;
        }
        Symmetry symmetry = sessions.active(actor.getUniqueId(), event.getWorld().getName());
        if (symmetry != null && actor.hasPermission(SymmetrySessions.PERMISSION)) {
            event.setExtent(new SymmetryExtent(event.getExtent(), symmetry, config.maxRange()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Symmetry symmetry = active(event.getPlayer(), event.getBlock().getWorld());
        if (symmetry == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        // A tick later: the second half of doors and beds is placed after the event
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Block> parts = parts(block);
            for (Block part : parts) {
                copy(symmetry, part);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Symmetry symmetry = active(event.getPlayer(), event.getBlock().getWorld());
        if (symmetry == null) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Symmetry.Copy copy : copies(symmetry, block)) {
                Block target = block.getWorld().getBlockAt(copy.position().x(), copy.position().y(), copy.position().z());
                // Only breaks the same block, not what was built there since
                if (target.getType() == type) {
                    // With physics, so that the other half of doors and beds goes too
                    target.setType(Material.AIR, true);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Symmetry symmetry = active(event.getPlayer(), event.getBlock().getWorld());
        if (symmetry == null) {
            return;
        }
        Block block = event.getBlock();
        Side side = event.getSide();
        List<Component> lines = new ArrayList<>(event.lines());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Symmetry.Copy copy : copies(symmetry, block)) {
                Block target = block.getWorld().getBlockAt(copy.position().x(), copy.position().y(), copy.position().z());
                if (target.getState() instanceof Sign sign) {
                    for (int i = 0; i < lines.size(); i++) {
                        sign.getSide(side).line(i, lines.get(i));
                    }
                    sign.update(true, false);
                }
            }
        });
    }

    @Nullable
    private Symmetry active(Player player, World world) {
        if (!sessions.config().vanilla() || !player.hasPermission(SymmetrySessions.PERMISSION)) {
            return null;
        }
        return sessions.active(player.getUniqueId(), world.getName());
    }

    private List<Symmetry.Copy> copies(Symmetry symmetry, Block block) {
        int minY = block.getWorld().getMinHeight();
        int maxY = block.getWorld().getMaxHeight() - 1;
        return symmetry.copies(block.getX(), block.getY(), block.getZ(), sessions.config().maxRange()).stream()
                .filter(copy -> copy.position().y() >= minY && copy.position().y() <= maxY)
                .toList();
    }

    /**
     * Places the copies of the block, with its tile entity (banner patterns, skull...).
     */
    private void copy(Symmetry symmetry, Block block) {
        BlockData data = block.getBlockData();
        if (data.getMaterial().isAir()) {
            return;
        }
        com.sk89q.worldedit.world.block.BlockState state = BukkitAdapter.adapt(data);
        BlockState source = block.getState();
        for (Symmetry.Copy copy : copies(symmetry, block)) {
            BlockVector3 position = copy.position();
            BlockState target = source.copy(block.getWorld().getBlockAt(position.x(), position.y(), position.z()).getLocation());
            target.setBlockData(BukkitAdapter.adapt(WorldEditMirror.transform(state, copy.transform())));
            // With physics, so that the stairs and fences around connect like for a block placed by hand
            target.update(true, true);
        }
    }

    /**
     * @return the block and the other half of a door, a tall plant or a bed
     */
    private static List<Block> parts(Block block) {
        BlockData data = block.getBlockData();
        Block other = null;
        if (data instanceof Bed bed) {
            other = block.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
        } else if (data instanceof Bisected bisected && !(data instanceof Stairs) && !(data instanceof TrapDoor)) {
            other = block.getRelative(bisected.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
        }
        return other != null && other.getType() == block.getType() ? List.of(block, other) : List.of(block);
    }
}

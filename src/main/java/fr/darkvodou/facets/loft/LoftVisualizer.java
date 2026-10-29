package fr.darkvodou.facets.loft;

import com.destroystokyo.paper.ParticleBuilder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows loft selections with particles: a dense outline and frames, and a looser net inside.
 * Control points have their own particles. Everything comes from {@link LoftConfig}.
 */
public final class LoftVisualizer implements Runnable {

    private record Cached(LoftRegion region, int version, List<Vector3> outline, List<Vector3> net,
                          List<BlockVector3> starts, List<BlockVector3> points) {
    }

    private final Map<UUID, Cached> cache = new HashMap<>();
    // Players who flipped the default visibility with //loft viz
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();
    private volatile LoftConfig config;

    public LoftVisualizer(LoftConfig config) {
        this.config = config;
    }

    /**
     * Applies a reloaded configuration: the particles are computed again.
     */
    public void setConfig(LoftConfig config) {
        this.config = config;
        cache.clear();
    }

    /**
     * @return true if the selection is now shown
     */
    public boolean toggle(UUID player) {
        if (!toggled.remove(player)) {
            toggled.add(player);
        }
        return isShown(player);
    }

    private boolean isShown(UUID player) {
        return config.shownByDefault() != toggled.contains(player);
    }

    @Override
    public void run() {
        LoftConfig config = this.config;
        cache.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            LoftRegionSelector selector = isShown(player.getUniqueId()) ? LoftSessions.peekSelector(player) : null;
            if (selector == null) {
                cache.remove(player.getUniqueId());
                continue;
            }
            show(player, cached(player.getUniqueId(), selector.getLoft(), config), config);
        }
    }

    private Cached cached(UUID id, LoftRegion region, LoftConfig config) {
        int version = region.getVersion();
        Cached cached = cache.get(id);
        if (cached != null && cached.region() == region && cached.version() == version) {
            return cached;
        }
        List<List<BlockVector3>> frames = region.getFrames();
        LoftOptions shape = region.fillOptions();
        List<BlockVector3> starts = new ArrayList<>();
        List<BlockVector3> points = new ArrayList<>();
        for (List<BlockVector3> frame : frames) {
            starts.add(frame.getFirst());
            points.addAll(frame.subList(1, frame.size()));
        }
        List<Vector3> outline = new ArrayList<>();
        List<Vector3> net = new ArrayList<>();
        if (!frames.isEmpty()) {
            lines(frames, shape, config, outline, net);
            thin(outline, net, config.maxParticles());
        }
        cached = new Cached(region, version, outline, net, starts, points);
        cache.put(id, cached);
        return cached;
    }

    private static void lines(List<List<BlockVector3>> frames, LoftOptions shape, LoftConfig config,
                              List<Vector3> outline, List<Vector3> net) {
        LoftSurface surface = new LoftSurface(frames, shape);
        surface.sampleOutline(config.outlineStep(), outline::add);
        surface.sampleFrames(config.outlineStep(), outline::add);

        // Net: rails from frame to frame, and curves following the frames in between
        double step = config.netStep();
        double spacing = config.netSpacing();
        int rails = Math.clamp(Math.round(surface.maxFrameLength() / spacing), 2, 24);
        for (int k = 1; k < rails; k++) {
            surface.rail((double) k / rails).sample(step, net::add);
        }
        int segments = surface.frameSegments();
        if (segments > 0) {
            int crosses = Math.clamp(Math.round(surface.rail(0.5).length() / spacing), segments, 64);
            for (int k = 1; k < crosses; k++) {
                // Frames are already drawn
                if (k * segments % crosses != 0) {
                    surface.sampleCross((double) k / crosses, step, net::add);
                }
            }
        }
    }

    /**
     * Keeps one particle out of n so that both lists hold at most {@code max} particles together.
     */
    private static void thin(List<Vector3> outline, List<Vector3> net, int max) {
        int total = outline.size() + net.size();
        if (total <= max) {
            return;
        }
        int stride = (total + max - 1) / max;
        thin(outline, stride);
        thin(net, stride);
    }

    private static void thin(List<Vector3> list, int stride) {
        List<Vector3> kept = new ArrayList<>(list.size() / stride + 1);
        for (int i = 0; i < list.size(); i += stride) {
            kept.add(list.get(i));
        }
        list.clear();
        list.addAll(kept);
    }

    private static void show(Player player, Cached cached, LoftConfig config) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        double maxSq = config.viewDistance() * config.viewDistance();
        List<Player> receivers = receivers(player, eye, maxSq, config);
        spawnAll(receivers, world, eye, maxSq, config.outline().builder(config.force()), cached.outline());
        spawnAll(receivers, world, eye, maxSq, config.net().builder(config.force()), cached.net());
        spawnBlocks(receivers, world, eye, maxSq, config.frameStart().builder(config.force()), cached.starts());
        spawnBlocks(receivers, world, eye, maxSq, config.point().builder(config.force()), cached.points());
    }

    /**
     * The player alone, or with the players around when selections are shown to others.
     */
    private static List<Player> receivers(Player player, Location eye, double maxSq, LoftConfig config) {
        if (!config.showToOthers()) {
            return List.of(player);
        }
        List<Player> receivers = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (other == player || other.getEyeLocation().distanceSquared(eye) <= maxSq) {
                receivers.add(other);
            }
        }
        return receivers;
    }

    private static void spawnAll(List<Player> receivers, World world, Location eye, double maxSq, ParticleBuilder builder,
                                 List<Vector3> positions) {
        builder.receivers(receivers);
        for (Vector3 p : positions) {
            spawn(world, eye, maxSq, builder, p.x(), p.y(), p.z());
        }
    }

    private static void spawnBlocks(List<Player> receivers, World world, Location eye, double maxSq, ParticleBuilder builder,
                                    List<BlockVector3> blocks) {
        builder.receivers(receivers);
        for (BlockVector3 block : blocks) {
            spawn(world, eye, maxSq, builder, block.x() + 0.5, block.y() + 0.5, block.z() + 0.5);
        }
    }

    private static void spawn(World world, Location eye, double maxSq, ParticleBuilder builder, double x, double y, double z) {
        double dx = eye.getX() - x;
        double dy = eye.getY() - y;
        double dz = eye.getZ() - z;
        if (dx * dx + dy * dy + dz * dz <= maxSq) {
            builder.location(world, x, y, z).spawn();
        }
    }
}

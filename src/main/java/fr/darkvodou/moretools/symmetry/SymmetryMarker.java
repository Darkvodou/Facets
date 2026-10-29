package fr.darkvodou.moretools.symmetry;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Shows each player their symmetry point: a small cross of particles in its block, orange when enabled, gray when paused.
 */
public final class SymmetryMarker implements Runnable {

    private static final double VIEW_DISTANCE = 128;
    private static final double ARM = 0.6;
    private static final double STEP = 0.2;
    private static final Particle.DustOptions ENABLED = new Particle.DustOptions(Color.fromRGB(0xff6a00), 0.8f);
    private static final Particle.DustOptions PAUSED = new Particle.DustOptions(Color.fromRGB(0x808080), 0.8f);

    private final SymmetrySessions sessions;

    public SymmetryMarker(SymmetrySessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void run() {
        if (!sessions.config().marker()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Symmetry symmetry = sessions.get(player.getUniqueId());
            if (symmetry == null || !symmetry.world().equals(player.getWorld().getName())) {
                continue;
            }
            Location center = new Location(player.getWorld(), symmetry.center().x() + 0.5, symmetry.center().y() + 0.5,
                    symmetry.center().z() + 0.5);
            if (center.distanceSquared(player.getLocation()) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            Particle.DustOptions dust = symmetry.enabled() ? ENABLED : PAUSED;
            for (double d = -ARM; d <= ARM + 1e-9; d += STEP) {
                player.spawnParticle(Particle.DUST, center.clone().add(d, 0, 0), 1, 0, 0, 0, 0, dust, true);
                player.spawnParticle(Particle.DUST, center.clone().add(0, d, 0), 1, 0, 0, 0, 0, dust, true);
                player.spawnParticle(Particle.DUST, center.clone().add(0, 0, d), 1, 0, 0, 0, 0, dust, true);
            }
        }
    }
}

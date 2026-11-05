package fr.darkvodou.facets.compat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

/**
 * The particles that depend on the server version.
 * <ul>
 *     <li>Trail particles standing still: a trail going to where it is stays there for its whole duration, without
 *     fading like dust. They come with Minecraft 1.21.4: before, {@link #TRAILS} is false and the callers use other
 *     particles.</li>
 *     <li>The force parameter of {@link Player#spawnParticle}, which shows a particle beyond the usual 32 blocks.
 *     It comes with Paper 1.21.4: before, the particles are sent without it.</li>
 * </ul>
 */
public final class Particles {

    public static final boolean TRAILS = trails();
    static final boolean FORCE = force();
    // Ticks a trail outlasts the refresh, so that the next ones are there before it goes
    private static final int OVERLAP = 4;

    private Particles() {
    }

    private static boolean trails() {
        try {
            return Particle.TRAIL != null && new Particle.Trail(new Location(null, 0, 0, 0), Color.WHITE, 1) != null;
        } catch (LinkageError e) {
            return false;
        }
    }

    private static boolean force() {
        try {
            Player.class.getMethod("spawnParticle", Particle.class, Location.class, int.class, double.class,
                    double.class, double.class, double.class, Object.class, boolean.class);
            return true;
        } catch (NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    /**
     * @return the data of a trail at the location, lasting until the next refresh. Only when {@link #TRAILS}
     */
    public static Object still(Location location, Color color, int refreshTicks) {
        return new Particle.Trail(location, color, refreshTicks + OVERLAP);
    }

    /**
     * Shows the player one particle standing at the location, from afar when the server can.
     */
    public static <T> void show(Player player, Particle particle, Location location, @Nullable T data) {
        if (FORCE) {
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0, data, true);
        } else {
            player.spawnParticle(particle, location, 1, 0, 0, 0, 0, data);
        }
    }
}

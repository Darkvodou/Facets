package fr.darkvodou.facets;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

/**
 * Trail particles standing still: a trail going to where it is stays there for its whole duration, without fading
 * like dust. They come with Minecraft 1.21.4: before, {@link #SUPPORTED} is false and the callers use other particles.
 */
public final class Trails {

    public static final boolean SUPPORTED = supported();
    // Ticks a trail outlasts the refresh, so that the next ones are there before it goes
    private static final int OVERLAP = 4;

    private Trails() {
    }

    private static boolean supported() {
        try {
            return Particle.TRAIL != null && new Particle.Trail(new Location(null, 0, 0, 0), Color.WHITE, 1) != null;
        } catch (LinkageError e) {
            return false;
        }
    }

    /**
     * @return the data of a trail at the location, lasting until the next refresh
     */
    public static Object still(Location location, Color color, int refreshTicks) {
        return new Particle.Trail(location, color, refreshTicks + OVERLAP);
    }
}

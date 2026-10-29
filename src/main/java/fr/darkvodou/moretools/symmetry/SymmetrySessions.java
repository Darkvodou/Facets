package fr.darkvodou.moretools.symmetry;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The symmetry of each player and the configuration. Read from FAWE's threads as well as the main one.
 */
public final class SymmetrySessions {

    public static final String PERMISSION = "moretools.symmetry";

    private final Map<UUID, Symmetry> symmetries = new ConcurrentHashMap<>();
    private volatile SymmetryConfig config;

    public SymmetrySessions(SymmetryConfig config) {
        this.config = config;
    }

    public SymmetryConfig config() {
        return config;
    }

    public void setConfig(SymmetryConfig config) {
        this.config = config;
    }

    @Nullable
    public Symmetry get(UUID player) {
        return symmetries.get(player);
    }

    public void set(UUID player, Symmetry symmetry) {
        symmetries.put(player, symmetry);
    }

    @Nullable
    public Symmetry remove(UUID player) {
        return symmetries.remove(player);
    }

    /**
     * @return the player's symmetry if it is enabled in this world, else null
     */
    @Nullable
    public Symmetry active(UUID player, String world) {
        Symmetry symmetry = symmetries.get(player);
        return symmetry != null && symmetry.enabled() && symmetry.world().equals(world) ? symmetry : null;
    }

    Map<UUID, Symmetry> all() {
        return symmetries;
    }
}

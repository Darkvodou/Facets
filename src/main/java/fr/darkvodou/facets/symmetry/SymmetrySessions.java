package fr.darkvodou.facets.symmetry;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * The symmetry of each player and the configuration. Read from FAWE's threads as well as the main one.
 */
public final class SymmetrySessions {

    public static final String PERMISSION = "facets.symmetry";

    // The symmetries shared in the chat, the latest ones only
    private static final int MAX_SHARES = 100;

    /**
     * A symmetry shared in the chat, as it was when shared.
     *
     * @param owner name of the player who shared it
     */
    public record Share(String owner, Symmetry symmetry) {
    }

    private final Map<UUID, Symmetry> symmetries = new ConcurrentHashMap<>();
    private final Map<Integer, Share> shares = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Share> eldest) {
            return size() > MAX_SHARES;
        }
    };
    private final AtomicInteger nextShare = new AtomicInteger(1);
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

    /**
     * Changes the player's symmetry, if they still have one.
     */
    public void update(UUID player, UnaryOperator<Symmetry> change) {
        symmetries.computeIfPresent(player, (uuid, symmetry) -> change.apply(symmetry));
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

    /**
     * @return the number given to the share, to take it back with {@link #share(int)}
     */
    public int share(String owner, Symmetry symmetry) {
        int id = nextShare.getAndIncrement();
        synchronized (shares) {
            shares.put(id, new Share(owner, symmetry));
        }
        return id;
    }

    /**
     * @return the share, or null if it is unknown or too old
     */
    @Nullable
    public Share share(int id) {
        synchronized (shares) {
            return shares.get(id);
        }
    }

    Map<UUID, Symmetry> all() {
        return symmetries;
    }
}

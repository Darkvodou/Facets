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

    /**
     * A second symmetry removed with //sym2 off, and the world of its first symmetry.
     */
    public record RemovedSecond(String world, Symmetry.Second second) {
    }

    private final Map<UUID, Symmetry> symmetries = new ConcurrentHashMap<>();
    // The last symmetry removed with //sym off, and the last second one removed with //sym2 off, to restore them
    private final Map<UUID, Symmetry> removed = new ConcurrentHashMap<>();
    private final Map<UUID, RemovedSecond> removedSeconds = new ConcurrentHashMap<>();
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
     * Removes the player's symmetry, keeping it to be restored with {@link #restore(UUID)}.
     *
     * @return the removed symmetry, or null if they had none
     */
    @Nullable
    public Symmetry off(UUID player) {
        Symmetry symmetry = symmetries.remove(player);
        if (symmetry != null) {
            removed.put(player, symmetry);
        }
        return symmetry;
    }

    /**
     * @return the last symmetry the player removed, or null
     */
    @Nullable
    public Symmetry restorable(UUID player) {
        return removed.get(player);
    }

    /**
     * Puts back the last symmetry the player removed, enabled. The symmetry it replaces, if any, can be restored in
     * turn.
     *
     * @return the restored symmetry, or null if there was none to restore
     */
    @Nullable
    public Symmetry restore(UUID player) {
        Symmetry restored = removed.remove(player);
        if (restored == null) {
            return null;
        }
        restored = restored.withEnabled(true);
        Symmetry replaced = symmetries.put(player, restored);
        if (replaced != null) {
            removed.put(player, replaced);
        }
        return restored;
    }

    /**
     * Keeps a removed second symmetry, to be restored with //sym2 restore.
     */
    public void keepSecond(UUID player, String world, Symmetry.Second second) {
        removedSeconds.put(player, new RemovedSecond(world, second));
    }

    /**
     * @return the last second symmetry the player removed, or null
     */
    @Nullable
    public RemovedSecond restorableSecond(UUID player) {
        return removedSeconds.get(player);
    }

    /**
     * @return the last second symmetry the player removed, no longer kept, or null
     */
    @Nullable
    public RemovedSecond takeSecond(UUID player) {
        return removedSeconds.remove(player);
    }

    /**
     * Forgets the player's symmetry and the ones they removed.
     */
    public void forget(UUID player) {
        symmetries.remove(player);
        removed.remove(player);
        removedSeconds.remove(player);
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

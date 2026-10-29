package fr.darkvodou.facets.loft;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.RegionSelector;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Access to the WorldEdit session and loft selection of a Bukkit player.
 */
public final class LoftSessions {

    // LocalSession.getRegionSelector(World) clears the selection when the world differs:
    // a passive read (particles) must not do that, so the field is read directly.
    private static final MethodHandle SELECTOR_GETTER;

    static {
        try {
            SELECTOR_GETTER = MethodHandles.privateLookupIn(LocalSession.class, MethodHandles.lookup())
                    .findGetter(LocalSession.class, "selector", RegionSelector.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private LoftSessions() {
    }

    static LocalSession session(Player player) {
        return WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
    }

    /**
     * Loft selection of the player in their current world, without creating or changing anything.
     */
    @Nullable
    static LoftRegionSelector peekSelector(Player player) {
        RegionSelector selector = peekAnySelector(player);
        if (!(selector instanceof LoftRegionSelector loft) || loft.getWorld() == null
                || !loft.getWorld().getName().equals(player.getWorld().getName())) {
            return null;
        }
        return loft;
    }

    /**
     * Selection mode of the player (any kind), without creating or changing anything.
     */
    @Nullable
    public static RegionSelector peekAnySelector(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player));
        if (session == null) {
            return null;
        }
        try {
            return (RegionSelector) SELECTOR_GETTER.invoke(session);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Loft selection of the player in their current world, as WorldEdit commands see it.
     */
    @Nullable
    static LoftRegionSelector selector(Player player) {
        RegionSelector selector = session(player).getRegionSelector(BukkitAdapter.adapt(player.getWorld()));
        return selector instanceof LoftRegionSelector loft ? loft : null;
    }
}

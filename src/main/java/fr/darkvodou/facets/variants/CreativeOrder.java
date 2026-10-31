package fr.darkvodou.facets.variants;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The order of the items in the creative inventory, read from the server's creative tabs (Bukkit has no API for it).
 * Minecraft's own names are used through reflection, since Paper runs with them on every supported version.
 */
final class CreativeOrder {

    private static volatile Map<Material, Integer> positions;

    private CreativeOrder() {
    }

    /**
     * By position in the creative tabs; the items missing from them come last, in Material order.
     */
    static Comparator<Material> comparator(Logger logger) {
        Map<Material, Integer> positions = positions(logger);
        return Comparator.comparingInt((Material material) -> positions.getOrDefault(material, Integer.MAX_VALUE))
                .thenComparingInt(Material::ordinal);
    }

    private static Map<Material, Integer> positions(Logger logger) {
        if (positions == null) {
            Map<Material, Integer> read = read(logger);
            positions = read == null ? Map.of() : read;
        }
        return positions;
    }

    @Nullable
    private static Map<Material, Integer> read(Logger logger) {
        try {
            Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
            Object server = serverClass.getMethod("getServer").invoke(null);
            Object worldData = server.getClass().getMethod("getWorldData").invoke(server);
            Object features = worldData.getClass().getMethod("enabledFeatures").invoke(worldData);
            Object registries = server.getClass().getMethod("registryAccess").invoke(server);

            Class<?> tabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
            Method rebuild = null;
            for (Method method : tabs.getMethods()) {
                if (method.getName().equals("tryRebuildTabContents") && method.getParameterCount() == 3) {
                    rebuild = method;
                }
            }
            if (rebuild == null) {
                throw new NoSuchMethodException("CreativeModeTabs.tryRebuildTabContents");
            }
            // With operator items, like the operator tab
            rebuild.invoke(null, features, true, registries);

            Method asBukkit = null;
            for (Method method : Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethods()) {
                if (method.getName().equals("asBukkitCopy") && method.getParameterCount() == 1) {
                    asBukkit = method;
                }
            }
            if (asBukkit == null) {
                throw new NoSuchMethodException("CraftItemStack.asBukkitCopy");
            }

            Map<Material, Integer> read = new EnumMap<>(Material.class);
            for (Object tab : (Collection<?>) tabs.getMethod("allTabs").invoke(null)) {
                // The category tabs, in the order they are shown: not the search, hotbar or inventory tabs
                if (!tab.getClass().getMethod("getType").invoke(tab).toString().equals("CATEGORY")) {
                    continue;
                }
                for (Object item : (Collection<?>) tab.getClass().getMethod("getDisplayItems").invoke(tab)) {
                    Material material = ((ItemStack) asBukkit.invoke(null, item)).getType();
                    read.putIfAbsent(material, read.size());
                }
            }
            return read;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.warning("Could not read the creative inventory order, variants keep the Material order: " + e);
            return null;
        }
    }
}

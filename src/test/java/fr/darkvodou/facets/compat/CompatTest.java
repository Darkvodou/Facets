package fr.darkvodou.facets.compat;

import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the compat classes against the API the plugin is compiled with, through stand-ins of the server objects.
 */
class CompatTest {

    /**
     * @return a stand-in of the interface, answering the calls by method name (null for the others)
     */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> answers, List<Object[]> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            calls.add(new Object[]{method.getName(), args == null ? new Object[0] : args});
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            }
            return answers.get(method.getName());
        });
    }

    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        return fake(type, answers, new ArrayList<>());
    }

    @Test
    void forceParameterFoundInTheApi() {
        // Paper 1.21.4+, which the plugin is compiled against
        assertTrue(Particles.FORCE);
    }

    @Test
    void particlesShownWithForce() {
        List<Object[]> calls = new ArrayList<>();
        Player player = fake(Player.class, Map.of(), calls);
        Location location = new Location(null, 1, 2, 3);
        Particles.show(player, null, location, "data");
        Object[] call = calls.getFirst();
        assertEquals("spawnParticle", call[0]);
        Object[] args = (Object[]) call[1];
        // particle, location, count, offsets, extra, data, force
        assertEquals(9, args.length);
        assertSame(location, args[1]);
        assertEquals(1, args[2]);
        assertEquals("data", args[7]);
        assertEquals(true, args[8]);
    }

    @Test
    void menuFoundFromTheEvent() {
        InventoryHolder menu = fake(InventoryHolder.class, Map.of());
        Inventory top = fake(Inventory.class, Map.of("getHolder", menu));
        Inventory bottom = fake(Inventory.class, Map.of());
        InventoryView view = fake(InventoryView.class, Map.of("getTopInventory", top, "getBottomInventory", bottom));
        assertSame(menu, Menus.holder(new InventoryEvent(view)));
    }

    @Test
    void menuFoundFromThePlayer() {
        InventoryHolder menu = fake(InventoryHolder.class, Map.of());
        Inventory top = fake(Inventory.class, Map.of("getHolder", menu));
        InventoryView view = fake(InventoryView.class, Map.of("getTopInventory", top));
        HumanEntity player = fake(HumanEntity.class, Map.of("getOpenInventory", view));
        assertSame(menu, Menus.openHolder(player));
        // The way taken when InventoryView is not what the plugin was compiled with
        assertSame(menu, Menus.reflectedOpenHolder(player));
    }
}

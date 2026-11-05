package fr.darkvodou.facets.compat;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * The inventory of an open menu, without going through {@code InventoryView}: a class before Minecraft 1.21 and an
 * interface since, so a call compiled for one fails on the other ({@link IncompatibleClassChangeError}).
 */
public final class Menus {

    private Menus() {
    }

    /**
     * @return the holder of the top inventory of the event (the chest of a menu, not the player's inventory)
     */
    @Nullable
    public static InventoryHolder holder(InventoryEvent event) {
        // getInventory() is the top inventory in every version
        return event.getInventory().getHolder();
    }

    /**
     * @return the holder of the top inventory the player has open, their own inventory when they have nothing open
     */
    @Nullable
    public static InventoryHolder openHolder(HumanEntity player) {
        try {
            return player.getOpenInventory().getTopInventory().getHolder();
        } catch (IncompatibleClassChangeError e) {
            return reflectedOpenHolder(player);
        }
    }

    @Nullable
    static InventoryHolder reflectedOpenHolder(HumanEntity player) {
        try {
            Object view = HumanEntity.class.getMethod("getOpenInventory").invoke(player);
            Method top = view.getClass().getMethod("getTopInventory");
            top.setAccessible(true);
            return ((Inventory) top.invoke(view)).getHolder();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}

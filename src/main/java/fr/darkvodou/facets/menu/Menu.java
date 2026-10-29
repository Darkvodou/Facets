package fr.darkvodou.facets.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An open Facets menu: a chest whose top row holds the tabs, each slot doing something when clicked.
 */
final class Menu implements InventoryHolder {

    static final int SIZE = 54;

    enum Tab {
        SYMMETRY, SELECTIONS, MASKS
    }

    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Tab tab;

    Menu(Component title, Tab tab) {
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        this.tab = tab;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    Tab tab() {
        return tab;
    }

    void setTab(Tab tab) {
        this.tab = tab;
    }

    void clear() {
        inventory.clear();
        actions.clear();
    }

    void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    void click(int slot, ClickType click) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(click);
        }
    }
}

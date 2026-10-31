package fr.darkvodou.facets.replace;

import fr.darkvodou.facets.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * //replacecolor [color] [new color]: changes the color of the blocks in the selection and keeps their state
 * (red_bed, red_shulker_box and red_wool_stairs become lime ones, facing the same way).
 * The colors left out are chosen in a chest: the color to replace, then the new one.
 */
public final class ReplaceColorCommand implements TabExecutor, Listener {

    private static final String MESSAGES = "replacecolor";
    // Two rows of 8 colors split in the middle, then the back button under the middle
    private static final int SIZE = 27;
    private static final int BACK = 22;

    private final Plugin plugin;

    public ReplaceColorCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final class View implements InventoryHolder {

        private final Inventory inventory;
        private final Map<Integer, String> colors = new HashMap<>();
        // The color to replace, null while it is not chosen
        private final String from;

        View(String from) {
            this.inventory = Bukkit.createInventory(this, SIZE, from == null
                    ? Messages.plain("replacecolor.title-from")
                    : Messages.plain("replacecolor.title-to", "color", from.replace('_', ' ')));
            this.from = from;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        if (args.length > 2) {
            Messages.send(player, "usage", "usage", "/" + label + " [color] [new color]");
            return true;
        }
        for (String arg : args) {
            if (!ColorReplace.COLORS.contains(arg.toLowerCase(Locale.ROOT))) {
                Messages.send(player, "replacecolor.unknown-color", "color", arg,
                        "colors", String.join(", ", ColorReplace.COLORS));
                return true;
            }
        }
        if (Replacement.selection(player, MESSAGES) == null) {
            return true;
        }
        if (args.length == 2) {
            replace(player, args[0].toLowerCase(Locale.ROOT), args[1].toLowerCase(Locale.ROOT));
        } else {
            open(player, args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 2) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return ColorReplace.COLORS.stream()
                .filter(color -> color.startsWith(last) && (args.length == 1 || !color.equalsIgnoreCase(args[0])))
                .toList();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != view.getInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (view.from != null && event.getSlot() == BACK) {
            open(player, null);
            return;
        }
        String color = view.colors.get(event.getSlot());
        if (color == null || color.equals(view.from)) {
            return;
        }
        if (view.from == null) {
            open(player, color);
        } else {
            player.closeInventory();
            replace(player, view.from, color);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    /**
     * Opens the chest choosing the color to replace, or the new one when the color to replace is given.
     */
    private static void open(Player player, String from) {
        View view = new View(from);
        ItemStack filler = Replacement.item(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of(), false);
        for (int slot = 0; slot < SIZE; slot++) {
            view.inventory.setItem(slot, filler);
        }
        for (int i = 0; i < ColorReplace.COLORS.size(); i++) {
            String color = ColorReplace.COLORS.get(i);
            int column = i % 8;
            int slot = i / 8 * 9 + (column < 4 ? column : column + 1);
            Component name = Component.translatable("color.minecraft." + color, NamedTextColor.YELLOW);
            List<Component> lore = from == null ? Messages.lines("replacecolor.from-lore")
                    : color.equals(from) ? Messages.lines("replacecolor.chosen-lore")
                    : Messages.lines("replacecolor.to-lore", "color", from.replace('_', ' '));
            Material wool = Material.valueOf(color.toUpperCase(Locale.ROOT) + "_WOOL");
            view.inventory.setItem(slot, Replacement.item(wool, name, lore, color.equals(from)));
            view.colors.put(slot, color);
        }
        if (from != null) {
            view.inventory.setItem(BACK, Replacement.item(Material.ARROW, Messages.plain("replacecolor.back"),
                    List.of(), false));
        }
        player.openInventory(view.getInventory());
    }

    private void replace(Player player, String from, String to) {
        if (from.equals(to)) {
            Messages.send(player, "replacecolor.same-color");
            return;
        }
        Replacement.apply(plugin, player, "/replacecolor", MESSAGES, name -> {
                    String recolored = ColorReplace.recolor(name, from, to);
                    return recolored == null ? List.of() : List.of(recolored);
                },
                "color", from.replace('_', ' '));
    }
}

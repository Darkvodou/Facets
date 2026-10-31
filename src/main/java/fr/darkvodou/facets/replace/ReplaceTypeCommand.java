package fr.darkvodou.facets.replace;

import fr.darkvodou.facets.Messages;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * //typereplace [type] [new type]: changes the type of the blocks in the selection and keeps their state (from
 * oak_planks, oak_stairs and oak_slab become spruce_stairs and spruce_slab, or diorite_stairs and diorite_slab).
 * The blocks with no version in the new type are left as they are.
 * The types left out are chosen in a chest: the type to replace, then the new one.
 */
public final class ReplaceTypeCommand implements TabExecutor, Listener {

    private static final String MESSAGES = "typereplace";
    private static final int SIZE = 54;
    // The last row holds the page arrows and the back button
    private static final int PAGE = 45;
    private static final int PREVIOUS = 45;
    private static final int BACK = 49;
    private static final int NEXT = 53;

    private final Plugin plugin;

    public ReplaceTypeCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final class View implements InventoryHolder {

        private final Inventory inventory;
        private final Map<Integer, String> types = new HashMap<>();
        // The type to replace, null while it is not chosen
        private final String from;
        private final int page;

        View(String from, int page) {
            this.inventory = Bukkit.createInventory(this, SIZE, from == null
                    ? Messages.plain("typereplace.title-from")
                    : Messages.plain("typereplace.title-to", "type", pretty(from)));
            this.from = from;
            this.page = page;
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
            Messages.send(player, "usage", "usage", "/" + label + " [type] [new type]");
            return true;
        }
        Map<String, List<Material>> types = TypeReplace.types();
        for (String arg : args) {
            if (!types.containsKey(arg.toLowerCase(Locale.ROOT))) {
                Messages.send(player, "typereplace.unknown-type", "type", arg);
                return true;
            }
        }
        if (Replacement.selection(player, MESSAGES) == null) {
            return true;
        }
        if (args.length == 2) {
            replace(player, args[0].toLowerCase(Locale.ROOT), args[1].toLowerCase(Locale.ROOT));
        } else {
            open(player, args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : null, 0);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 2) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return TypeReplace.types().keySet().stream()
                .filter(type -> type.startsWith(last) && (args.length == 1 || !type.equalsIgnoreCase(args[0])))
                .sorted()
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
        int slot = event.getSlot();
        int pages = pages(TypeReplace.types().size());
        if (slot == PREVIOUS && view.page > 0) {
            open(player, view.from, view.page - 1);
            return;
        }
        if (slot == NEXT && view.page < pages - 1) {
            open(player, view.from, view.page + 1);
            return;
        }
        if (slot == BACK && view.from != null) {
            open(player, null, 0);
            return;
        }
        String type = view.types.get(slot);
        if (type == null || type.equals(view.from)) {
            return;
        }
        if (view.from == null) {
            open(player, type, 0);
        } else {
            player.closeInventory();
            replace(player, view.from, type);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    /**
     * Opens the chest choosing the type to replace, or the new one when the type to replace is given.
     */
    private void open(Player player, String from, int page) {
        List<Map.Entry<String, List<Material>>> types =
                new ArrayList<>(TypeReplace.types().entrySet());
        int pages = pages(types.size());
        View view = new View(from, Math.clamp(page, 0, pages - 1));
        for (int i = 0; i < PAGE && view.page * PAGE + i < types.size(); i++) {
            Map.Entry<String, List<Material>> entry = types.get(view.page * PAGE + i);
            String type = entry.getKey();
            int count = entry.getValue().size();
            List<Component> lore = from == null ? Messages.lines("typereplace.from-lore", "count", count)
                    : type.equals(from) ? Messages.lines("typereplace.chosen-lore", "count", count)
                    : Messages.lines("typereplace.to-lore", "count", count, "type", pretty(from));
            view.inventory.setItem(i, Replacement.item(entry.getValue().getFirst(),
                    Messages.plain("typereplace.type", "type", pretty(type)), lore, type.equals(from)));
            view.types.put(i, type);
        }

        ItemStack filler = Replacement.item(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of(), false);
        for (int slot = PAGE; slot < SIZE; slot++) {
            view.inventory.setItem(slot, filler);
        }
        if (view.page > 0) {
            view.inventory.setItem(PREVIOUS, Replacement.item(Material.ARROW,
                    Messages.plain("typereplace.previous", "page", view.page, "pages", pages), List.of(), false));
        }
        if (view.page < pages - 1) {
            view.inventory.setItem(NEXT, Replacement.item(Material.ARROW,
                    Messages.plain("typereplace.next", "page", view.page + 2, "pages", pages), List.of(), false));
        }
        if (from != null) {
            view.inventory.setItem(BACK, Replacement.item(Material.BARRIER, Messages.plain("typereplace.back"),
                    List.of(), false));
        }
        player.openInventory(view.getInventory());
    }

    private void replace(Player player, String from, String to) {
        if (from.equals(to)) {
            Messages.send(player, "typereplace.same-type");
            return;
        }
        Replacement.apply(plugin, player, "/typereplace", MESSAGES,
                name -> TypeReplace.retype(name, from, to),
                "type", pretty(from));
    }

    private static int pages(int types) {
        return Math.max(1, (types + PAGE - 1) / PAGE);
    }

    private static String pretty(String type) {
        return type.replace('_', ' ');
    }
}

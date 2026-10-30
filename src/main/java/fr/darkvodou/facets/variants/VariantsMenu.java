package fr.darkvodou.facets.variants;

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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * /variant and /family: a chest with the variants of the block in hand (oak_stairs: every stairs)
 * or its family (oak_stairs: every oak block), two buttons switching between them.
 * A left click puts the variant in hand, a right click adds a stack of it to the inventory.
 */
public final class VariantsMenu implements TabExecutor, Listener {

    private static final int SIZE = 54;
    // The last row holds the page arrows and the view switch
    private static final int PAGE = 45;

    private final Logger logger;

    public VariantsMenu(Logger logger) {
        this.logger = logger;
    }

    private static final class View implements InventoryHolder {

        private final Inventory inventory;
        private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        private final Material origin;
        private boolean byShape;
        private int page;

        View(Material origin) {
            this.inventory = Bukkit.createInventory(this, SIZE, Messages.plain("variants.title"));
            this.origin = origin;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void set(int slot, ItemStack item, Consumer<ClickType> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        if (!Variants.holdable(held)) {
            Messages.send(player, "variants.not-a-block");
            return true;
        }
        // /variant: every block of the same shape, /family: every block of the same family
        boolean byShape = command.getName().equals("variant");
        if ((byShape ? Variants.sameShape(held) : Variants.sameFamily(held)).size() <= 1) {
            Messages.send(player, byShape ? "variants.no-variant" : "variants.no-family", "block", held.getKey().getKey());
            return true;
        }
        View view = new View(held);
        view.byShape = byShape;
        render(view, player);
        player.openInventory(view.getInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == view.getInventory() && event.getWhoClicked() instanceof Player) {
            Consumer<ClickType> action = view.actions.get(event.getSlot());
            if (action != null) {
                action.accept(event.getClick());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    private void render(View view, Player player) {
        view.inventory.clear();
        view.actions.clear();
        // In the order of the creative inventory
        List<Material> variants = (view.byShape ? Variants.sameShape(view.origin) : Variants.sameFamily(view.origin))
                .stream().sorted(CreativeOrder.comparator(logger)).toList();
        int pages = Math.max(1, (variants.size() + PAGE - 1) / PAGE);
        view.page = Math.min(view.page, pages - 1);
        Material held = player.getInventory().getItemInMainHand().getType();
        for (int i = 0; i < PAGE && view.page * PAGE + i < variants.size(); i++) {
            Material variant = variants.get(view.page * PAGE + i);
            view.set(i, item(variant, null, Messages.lines("variants.variant-lore"), variant == held),
                    click -> choose(player, variant, click));
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of(), false);
        for (int slot = PAGE; slot < SIZE; slot++) {
            view.inventory.setItem(slot, filler);
        }
        if (view.page > 0) {
            view.set(45, item(Material.ARROW, Messages.plain("variants.previous", "page", view.page, "pages", pages),
                    List.of(), false), click -> {
                view.page--;
                render(view, player);
            });
        }
        if (view.page < pages - 1) {
            view.set(53, item(Material.ARROW, Messages.plain("variants.next", "page", view.page + 2, "pages", pages),
                    List.of(), false), click -> {
                view.page++;
                render(view, player);
            });
        }
        Variants.Key key = Variants.key(view.origin.name());
        viewButton(view, player, 48, false, Material.OAK_PLANKS,
                Messages.plain("variants.by-family", "family", key.family()), Variants.sameFamily(view.origin).size());
        viewButton(view, player, 50, true, Material.OAK_STAIRS,
                Messages.plain("variants.by-shape", "shape", key.shape()), Variants.sameShape(view.origin).size());
    }

    /**
     * A button showing the family or the variants, shining when shown and disabled when there is nothing else to show.
     */
    private void viewButton(View view, Player player, int slot, boolean byShape, Material icon, Component name, int count) {
        boolean shown = view.byShape == byShape;
        String lore = shown ? "variants.view-shown" : count > 1 ? "variants.view-lore" : "variants.view-empty";
        view.set(slot, item(icon, name, Messages.lines(lore, "count", count), shown), click -> {
            if (!shown && count > 1) {
                view.byShape = byShape;
                view.page = 0;
                render(view, player);
            }
        });
    }

    private static void choose(Player player, Material variant, ClickType click) {
        if (click.isRightClick()) {
            player.getInventory().addItem(new ItemStack(variant, variant.getMaxStackSize()));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int amount = Math.clamp(hand.getAmount(), 1, variant.getMaxStackSize());
        player.getInventory().setItemInMainHand(new ItemStack(variant, amount));
        player.closeInventory();
    }

    /**
     * @param name the item name, or null for the block's own (translated) name
     */
    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            meta.displayName(name);
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}

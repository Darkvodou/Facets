package fr.darkvodou.facets.menu;

import com.fastasyncworldedit.core.regions.selector.FuzzyRegionSelector;
import com.fastasyncworldedit.core.regions.selector.PolyhedralRegionSelector;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.CylinderRegionSelector;
import com.sk89q.worldedit.regions.selector.EllipsoidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.regions.selector.SphereRegionSelector;
import fr.darkvodou.facets.Messages;
import fr.darkvodou.facets.loft.LoftRegionSelector;
import fr.darkvodou.facets.loft.LoftSessions;
import fr.darkvodou.facets.loft.LoftShape;
import fr.darkvodou.facets.symmetry.Symmetry;
import fr.darkvodou.facets.symmetry.SymmetrySessions;
import fr.darkvodou.facets.symmetry.SymmetryType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 * The Facets menu, opened with a right click of the menu item (a nether star by default).
 * Its buttons run the usual commands, so their messages and permissions apply.
 */
public final class MenuListener implements Listener {

    public static final String PERMISSION = "facets.menu";

    // //gmask [mask] and /gmask [mask]
    private static final java.util.regex.Pattern GMASK = java.util.regex.Pattern.compile(
            "^//?gmask(?:\\s+(.*))?$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final int[] TAB_SLOTS = {2, 4, 6};
    // First slot of the content, under the tabs and the separator row
    private static final int CONTENT = 18;

    /**
     * A selection mode of //sel.
     */
    private record Selection(String id, Material icon, Predicate<RegionSelector> current, @Nullable String permission) {
    }

    private static final List<Selection> SELECTIONS = List.of(
            new Selection("cuboid", Material.WOODEN_AXE,
                    s -> s instanceof CuboidRegionSelector && !(s instanceof ExtendingCuboidRegionSelector), null),
            new Selection("extend", Material.PISTON, s -> s instanceof ExtendingCuboidRegionSelector, null),
            new Selection("poly", Material.PRISMARINE_SHARD, s -> s instanceof Polygonal2DRegionSelector, null),
            new Selection("ellipsoid", Material.SLIME_BALL,
                    s -> s instanceof EllipsoidRegionSelector && !(s instanceof SphereRegionSelector), null),
            new Selection("sphere", Material.HEART_OF_THE_SEA, s -> s instanceof SphereRegionSelector, null),
            new Selection("cyl", Material.CAULDRON, s -> s instanceof CylinderRegionSelector, null),
            new Selection("convex", Material.AMETHYST_SHARD, s -> s instanceof ConvexPolyhedralRegionSelector, null),
            new Selection("polyhedral", Material.END_CRYSTAL, s -> s instanceof PolyhedralRegionSelector, null),
            new Selection("fuzzy", Material.FEATHER, s -> s instanceof FuzzyRegionSelector, null),
            new Selection("loft", Material.STRING, loft(LoftShape.SMOOTH), "facets.loft"),
            new Selection("loftpoly", Material.LEAD, loft(LoftShape.POLY), "facets.loft"),
            new Selection("loftfacet", Material.GLASS_PANE, loft(LoftShape.FACET), "facets.loft"));
    private static final int[] SELECTION_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 31, 32, 33};

    private static final Material[] TYPE_ICONS = {Material.CLOCK, Material.RED_DYE, Material.LIME_DYE,
            Material.LIGHT_BLUE_DYE, Material.OAK_FENCE, Material.SCAFFOLDING, Material.FIREWORK_STAR, Material.END_ROD,
            Material.BEACON};

    private final Plugin plugin;
    private final SymmetrySessions symmetries;
    private final MaskFavorites favorites;
    private volatile Material item;
    // Tab shown when each player opens the menu again
    private final Map<UUID, Menu.Tab> lastTabs = new ConcurrentHashMap<>();

    public MenuListener(Plugin plugin, SymmetrySessions symmetries, MaskFavorites favorites, Material item) {
        this.plugin = plugin;
        this.symmetries = symmetries;
        this.favorites = favorites;
        this.item = item;
    }

    public void setItem(Material item) {
        this.item = item;
    }

    private static Predicate<RegionSelector> loft(LoftShape shape) {
        return s -> s instanceof LoftRegionSelector loft && loft.getLoft().getShape() == shape;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getItem() == null || event.getItem().getType() != item
                || !event.getPlayer().hasPermission(PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer(), lastTabs.getOrDefault(event.getPlayer().getUniqueId(), Menu.Tab.SYMMETRY));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Matcher matcher = GMASK.matcher(event.getMessage().trim());
        if (matcher.matches()) {
            favorites.setCurrent(event.getPlayer().getUniqueId(), matcher.group(1));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == menu.getInventory() && event.getWhoClicked() instanceof Player player) {
            menu.click(event.getSlot(), event.getClick());
            // Commands take effect at once or on FAWE's thread: show the result a moment later
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
                    render(menu, player);
                }
            }, 3);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    public void open(Player player, Menu.Tab tab) {
        Menu menu = new Menu(Messages.plain("menu.title"), tab);
        render(menu, player);
        player.openInventory(menu.getInventory());
    }

    private void render(Menu menu, Player player) {
        menu.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of(), false);
        for (int slot = 0; slot < CONTENT; slot++) {
            menu.set(slot, filler);
        }
        Menu.Tab[] tabs = Menu.Tab.values();
        Material[] tabIcons = {Material.PRISMARINE_CRYSTALS, Material.WOODEN_AXE, Material.GLASS};
        for (int i = 0; i < tabs.length; i++) {
            Menu.Tab tab = tabs[i];
            String key = "menu.tab." + tab.name().toLowerCase(Locale.ROOT);
            menu.set(TAB_SLOTS[i], item(tabIcons[i], Messages.plain(key), List.of(), tab == menu.tab()), click -> {
                menu.setTab(tab);
                lastTabs.put(player.getUniqueId(), tab);
                render(menu, player);
            });
        }
        switch (menu.tab()) {
            case SELECTIONS -> selections(menu, player);
            case MASKS -> masks(menu, player);
            case SYMMETRY -> symmetry(menu, player);
        }
    }

    private void selections(Menu menu, Player player) {
        RegionSelector current = LoftSessions.peekAnySelector(player);
        int slot = 0;
        for (Selection selection : SELECTIONS) {
            if (selection.permission() != null && !player.hasPermission(selection.permission())) {
                continue;
            }
            boolean selected = current != null && selection.current().test(current);
            menu.set(SELECTION_SLOTS[slot++], item(selection.icon(),
                    Messages.plain("menu.selections.name", "name", selection.id()),
                    Messages.lines("menu.selections." + selection.id()), selected),
                    click -> player.chat("//sel " + selection.id()));
        }
        menu.set(49, item(Material.BARRIER, Messages.plain("menu.selections.clear"),
                Messages.lines("menu.selections.clear-lore"), false), click -> player.chat("//sel"));
    }

    private void masks(Menu menu, Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player));
        boolean hasMask = session != null && session.getMask() != null;
        String text = hasMask ? favorites.current(player.getUniqueId()) : null;
        List<Component> lore = !hasMask ? Messages.lines("menu.masks.current-none")
                : text == null ? Messages.lines("menu.masks.current-unknown") : Messages.lines("menu.masks.current-lore", "mask", text);
        menu.set(20, item(Material.ENDER_EYE, Messages.plain("menu.masks.current"), lore, hasMask));

        if (text != null) {
            menu.set(22, item(Material.WRITABLE_BOOK, Messages.plain("menu.masks.save"),
                    Messages.lines("menu.masks.save-lore", "mask", text), false), click -> {
                if (favorites.add(player.getUniqueId(), text)) {
                    Messages.send(player, "menu.masks.saved", "mask", text);
                } else {
                    Messages.send(player, "menu.masks.not-saved", "max", MaskFavorites.MAX);
                }
            });
        }
        menu.set(24, item(Material.BARRIER, Messages.plain("menu.masks.clear"), Messages.lines("menu.masks.clear-lore"),
                false), click -> player.chat("//gmask"));

        List<String> saved = favorites.get(player.getUniqueId());
        if (saved.isEmpty()) {
            menu.set(40, item(Material.PAPER, Messages.plain("menu.masks.no-favorite"),
                    Messages.lines("menu.masks.no-favorite-lore"), false));
        }
        for (int i = 0; i < saved.size() && i < MaskFavorites.MAX; i++) {
            String mask = saved.get(i);
            menu.set(36 + i, item(Material.PAPER, Messages.plain("menu.masks.favorite", "mask", mask),
                    Messages.lines("menu.masks.favorite-lore"), mask.equals(text)), click -> {
                if (click.isRightClick()) {
                    favorites.remove(player.getUniqueId(), mask);
                    render(menu, player);
                } else {
                    player.chat("//gmask " + mask);
                }
            });
        }
    }

    private void symmetry(Menu menu, Player player) {
        Symmetry symmetry = symmetries.get(player.getUniqueId());
        int maxRange = symmetries.config().maxRange();
        if (symmetry == null) {
            menu.set(19, item(Material.COMPASS, Messages.plain("menu.symmetry.status"),
                    Messages.lines("menu.symmetry.status-none"), false));
        } else {
            menu.set(19, item(Material.COMPASS, Messages.plain("menu.symmetry.status"),
                    Messages.lines(symmetry.enabled() ? "menu.symmetry.status-on" : "menu.symmetry.status-off",
                            "type", symmetry.type().id(), "copies", symmetry.type().copies(), "world", symmetry.world(),
                            "position", symmetry.center().x() + ", " + symmetry.center().y() + ", " + symmetry.center().z(),
                            "range", symmetry.range(maxRange)), symmetry.enabled()));
        }
        menu.set(21, item(Material.TARGET, Messages.plain("menu.symmetry.set"), Messages.lines("menu.symmetry.set-lore"),
                false), click -> player.chat("//sym set"));
        if (symmetry != null) {
            menu.set(22, item(symmetry.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    Messages.plain(symmetry.enabled() ? "menu.symmetry.pause" : "menu.symmetry.resume"),
                    Messages.lines("menu.symmetry.toggle-lore"), false), click -> player.chat("//sym toggle"));
            menu.set(23, item(Material.BARRIER, Messages.plain("menu.symmetry.remove"),
                    Messages.lines("menu.symmetry.remove-lore"), false), click -> player.chat("//sym off"));
        }

        SymmetryType[] types = SymmetryType.values();
        for (int i = 0; i < types.length; i++) {
            SymmetryType type = types[i];
            boolean selected = symmetry != null && symmetry.type() == type;
            menu.set(27 + i, item(TYPE_ICONS[i], Messages.plain("menu.symmetry.type", "type", type.id()),
                    Messages.lines("menu.symmetry.type-" + type.id(), "copies", type.copies()), selected),
                    click -> player.chat(symmetry == null ? "//sym set " + type.id() : "//sym type " + type.id()));
        }

        if (symmetry != null) {
            int range = symmetry.range(maxRange);
            int[] steps = {-16, -1, 1, 16};
            int[] slots = {38, 39, 41, 42};
            for (int i = 0; i < steps.length; i++) {
                int step = steps[i];
                int target = Math.max(1, Math.min(maxRange, range + step));
                menu.set(slots[i], item(step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                        Messages.plain("menu.symmetry.range-step", "step", (step > 0 ? "+" : "") + step),
                        Messages.lines("menu.symmetry.range-step-lore", "range", target), false),
                        click -> player.chat("//sym range " + target));
            }
            menu.set(40, item(Material.SPYGLASS, Messages.plain("menu.symmetry.range", "range", range),
                    Messages.lines("menu.symmetry.range-lore", "max", maxRange), false), click -> player.chat("//sym range max"));
        }
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}

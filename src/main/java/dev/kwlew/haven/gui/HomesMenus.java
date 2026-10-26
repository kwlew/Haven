package dev.kwlew.haven.gui;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeDisplay;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.home.WorldNames;
import dev.kwlew.haven.home.WorldRules;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.share.HomeSharing;
import dev.kwlew.haven.share.SharedVisits;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import dev.kwlew.haven.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The optional homes menu: a read-only chest that lists a player's homes and launches a teleport.
 * <p>
 * It is deliberately a viewer and nothing more. Every click in or over the menu is cancelled, and
 * the only actions are "teleport to this home" and "change page"; editing stays with the commands.
 * A teleport goes through {@link TeleportService#teleport} exactly as {@code /home} does, so
 * warmup, cooldown and the safe-teleport check apply unchanged.
 * <p>
 * Open menus are tracked per player rather than found by asking the server what a player has open.
 * That keeps the click handlers to a map lookup, avoids {@code InventoryView}, whose type changed
 * from a class to an interface in 1.21, and gives {@link #closeAll} its list.
 */
public class HomesMenus implements Listener, LifecycleComponent {

    /** Granted by default; lets a server keep the chat list for some ranks. */
    public static final String PERMISSION = "haven.gui";

    private static final String HOME_PERMISSION = "haven.home";

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final Messages messages;
    private final HomeManager homeManager;
    private final HomeLimits limits;
    private final WorldRules worldRules;
    private final TeleportService teleportService;
    private final HomeSharing sharing;
    private final SharedVisits visits;
    private final Sounds sounds;

    private final Map<UUID, HomesMenu> open = new HashMap<>();

    public HomesMenus(JavaPlugin plugin,
                      HavenConfig config,
                      Messages messages,
                      HomeManager homeManager,
                      HomeLimits limits,
                      WorldRules worldRules,
                      TeleportService teleportService,
                      HomeSharing sharing,
                      SharedVisits visits,
                      Sounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.homeManager = homeManager;
        this.limits = limits;
        this.worldRules = worldRules;
        this.teleportService = teleportService;
        this.sharing = sharing;
        this.visits = visits;
        this.sounds = sounds;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        closeAll();
    }

    /**
     * True when {@code /homes} and {@code /haven} should open the menu for this player rather than
     * print the chat list.
     */
    public boolean available(Player player) {
        return config.guiEnabled() && player.hasPermission(PERMISSION);
    }

    /**
     * Opens the menu, or tells the player they have no homes. An empty chest of border glass would
     * read as a bug, and the empty-list message already says what to do next. Homes shared with the
     * player count as something to show.
     */
    public void open(Player player) {
        PlayerHomes homes = homeManager.homesOf(player);
        int total = homes.count() + sharing.visibleHints(homes).size();

        if (total == 0) {
            messages.send(player, "home.list-empty");
            return;
        }

        int rows = MenuLayout.rowsFor(total);
        HomesMenu menu = new HomesMenu(rows);
        Inventory inventory = Bukkit.createInventory(menu, rows * MenuLayout.COLUMNS,
                messages.legacy("gui.title"));
        menu.inventory(inventory);

        render(player, menu, homes, 0);
        player.openInventory(inventory);

        // After opening: opening over an existing menu closes it first, and that close event
        // would otherwise remove this entry.
        open.put(player.getUniqueId(), menu);
    }

    /**
     * Closes every open menu. Used on shutdown, and when a reload switches the menu off so nobody
     * keeps a menu the server no longer offers.
     */
    public void closeAll() {
        for (UUID id : List.copyOf(open.keySet())) {
            Player player = Bukkit.getPlayer(id);

            if (player != null) {
                player.closeInventory();
            }
        }

        open.clear();
    }

    /** Called after a reload; closes menus only if the feature was just turned off. */
    public void closeAllIfDisabled() {
        if (!config.guiEnabled()) {
            closeAll();
        }
    }

    // Highest, so a plugin that un-cancels an event at a lower priority can't let items be taken.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomesMenu)) {
            return;
        }

        // Every click while the menu is the top inventory, wherever it lands: shift-clicking from
        // the player's own inventory, number-key swaps and double-click collection all move items
        // into or out of the menu without ever clicking a menu slot. The decision rests on the
        // holder alone, so a missing or stale tracking entry can never let an item out.
        event.setCancelled(true);

        HomesMenu menu = trackedMenu(event.getWhoClicked(), event.getInventory());
        int slot = event.getRawSlot();

        if (menu == null
                || !(event.getWhoClicked() instanceof Player player)
                || slot < 0 || slot >= event.getInventory().getSize()
                || !(event.getClick().isLeftClick() || event.getClick().isRightClick())) {
            return;
        }

        int rows = menu.rows();

        if (slot == MenuLayout.previousSlot(rows) && menu.page() > 0) {
            turnPage(player, menu, menu.page() - 1);
            return;
        }

        if (slot == MenuLayout.nextSlot(rows)) {
            turnPage(player, menu, menu.page() + 1);
            return;
        }

        HomesMenu.Entry entry = menu.homeAt(slot);

        if (entry != null && menu.claim()) {
            // A tick later: swapping or closing an inventory from inside its own click event can
            // desync the client's cursor, and the teleport has no reason to rush ahead of it.
            Bukkit.getScheduler().runTask(plugin, () -> launch(player, menu, entry));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HomesMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        HomesMenu menu = open.get(id);

        if (menu != null && event.getInventory().getHolder() == menu) {
            open.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // In case no close event fires for a disconnecting player.
        open.remove(event.getPlayer().getUniqueId());
    }

    /**
     * The tracked menu for a player, if {@code top} belongs to it. Matched by holder, which is our
     * own object, so a stale entry can never act for a different menu. Used only to decide what a
     * click does; whether it is cancelled never depends on this.
     */
    private HomesMenu trackedMenu(HumanEntity who, Inventory top) {
        HomesMenu menu = open.get(who.getUniqueId());

        return menu != null && top.getHolder() == menu ? menu : null;
    }

    private void turnPage(Player player, HomesMenu menu, int page) {
        render(player, menu, homeManager.homesOf(player), page);
    }

    /**
     * Closes the menu and teleports. The home is looked up again by name because it may have been
     * deleted or renamed since the menu was drawn. A shared home goes down the same path as
     * {@code /home owner:home}, which asks the owner's data whether it is still shared.
     */
    private void launch(Player player, HomesMenu menu, HomesMenu.Entry entry) {
        if (!player.isOnline()) {
            return;
        }

        // Only close what we opened; the player may have since opened something else.
        if (open.get(player.getUniqueId()) == menu) {
            player.closeInventory();
        }

        // The menu is a shortcut for /home, so it must not be a way around that permission.
        if (!player.hasPermission(HOME_PERMISSION)) {
            messages.send(player, "general.no-permission");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (entry.isShared()) {
            visits.visit(player, entry.owner(), entry.ownerName(), entry.name());
            return;
        }

        Home home = homeManager.homesOf(player).get(entry.name());

        if (home == null) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", Home.normalize(entry.name())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // The menu is a shortcut for /home, so it must not be a way around the world rules either.
        if (!worldRules.teleportAllowed(player, home.world())) {
            messages.send(player, "teleport.world-disabled",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("world", WorldNames.displayName(messages, home.world())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        teleportService.teleport(player, home);
    }

    /**
     * Draws one page into the menu's inventory, replacing whatever it held.
     * <p>
     * The chest size is fixed when the menu opens, so paging re-renders in place. The page is
     * clamped, since homes may have been deleted while the menu was open. Shared homes follow the
     * player's own and are paged with them.
     */
    private void render(Player player, HomesMenu menu, PlayerHomes homes, int requestedPage) {
        Inventory inventory = menu.getInventory();
        int rows = menu.rows();

        List<Home> ordered = new ArrayList<>();
        WorldNames.groupByWorld(homes).values().forEach(ordered::addAll);

        List<PlayerHomes.Received> shared = sharing.visibleHints(homes);
        int own = ordered.size();
        int total = own + shared.size();
        int pages = MenuLayout.pageCount(total, rows);
        int page = MenuLayout.clampPage(requestedPage, total, rows);
        menu.page(page);

        inventory.clear();

        ItemStack border = borderPane();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / MenuLayout.COLUMNS;
            int column = slot % MenuLayout.COLUMNS;

            if (row == 0 || row == rows - 1 || column == 0 || column == MenuLayout.COLUMNS - 1) {
                inventory.setItem(slot, border);
            }
        }

        int capacity = MenuLayout.capacity(rows);
        int from = page * capacity;
        int to = Math.min(total, from + capacity);
        HomesMenu.Entry[] entries = new HomesMenu.Entry[Math.max(0, to - from)];

        for (int i = from; i < to; i++) {
            if (i < own) {
                Home home = ordered.get(i);

                inventory.setItem(MenuLayout.slotFor(i - from), homeItem(home));
                entries[i - from] = HomesMenu.Entry.own(home.name());
            } else {
                PlayerHomes.Received hint = shared.get(i - own);

                inventory.setItem(MenuLayout.slotFor(i - from), sharedItem(hint));
                entries[i - from] = HomesMenu.Entry.shared(hint.home(), hint.owner(), hint.ownerName());
            }
        }
        menu.entries(entries);

        if (page > 0) {
            inventory.setItem(MenuLayout.previousSlot(rows),
                    named(Material.ARROW, messages.legacyItem("gui.previous-page"), null));
        }
        if (page < pages - 1) {
            inventory.setItem(MenuLayout.nextSlot(rows),
                    named(Material.ARROW, messages.legacyItem("gui.next-page"), null));
        }

        TagResolver[] info = {
                // Own homes only: shared ones don't use up the player's limit.
                Placeholder.unparsed("count", Integer.toString(own)),
                Placeholder.unparsed("max", limits.describe(limits.limitFor(player))),
                Placeholder.unparsed("page", Integer.toString(page + 1)),
                Placeholder.unparsed("pages", Integer.toString(pages))
        };
        inventory.setItem(MenuLayout.infoSlot(rows),
                named(Material.BOOK, messages.legacyItem("gui.info-name", info),
                        messages.legacyItemLines("gui.info-lore", info)));
    }

    private ItemStack homeItem(Home home) {
        TagResolver[] placeholders = HomeDisplay.placeholders(home,
                WorldNames.displayName(messages, home.world()));

        return named(materialFor(home), messages.legacyItem("gui.home-name", placeholders),
                messages.legacyItemLines("gui.home-lore", placeholders));
    }

    /**
     * A shared home. A plain player head with no skull owner: setting one makes the server fetch a
     * profile, and the hint carries no coordinates or world to show either.
     */
    private ItemStack sharedItem(PlayerHomes.Received hint) {
        TagResolver[] placeholders = {
                Placeholder.unparsed("owner", hint.ownerName()),
                Placeholder.unparsed("name", hint.home())
        };

        return named(Material.PLAYER_HEAD, messages.legacyItem("gui.shared-name", placeholders),
                messages.legacyItemLines("gui.shared-lore", placeholders));
    }

    /**
     * Icon by dimension so the Nether and End read differently at a glance, matching the chat
     * list's coloured world lines. A world that is not loaded gets a barrier: the home is
     * orphaned, and clicking it reports that through the normal teleport path.
     */
    private Material materialFor(Home home) {
        World world = Bukkit.getWorld(home.world());

        if (world == null) {
            return Material.BARRIER;
        }

        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private ItemStack borderPane() {
        return named(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }
}

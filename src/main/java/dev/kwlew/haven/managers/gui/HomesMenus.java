package dev.kwlew.haven.managers.gui;

import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.managers.config.HavenConfig;
import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeDisplay;
import dev.kwlew.haven.managers.home.HomeLimits;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.home.WorldNames;
import dev.kwlew.haven.managers.home.WorldRules;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.share.HomeSharing;
import dev.kwlew.haven.managers.share.SharedVisits;
import dev.kwlew.haven.managers.sound.HavenSound;
import dev.kwlew.haven.managers.sound.Sounds;
import dev.kwlew.haven.managers.teleport.TeleportService;
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
 * Read-only homes chest: every click is cancelled, and the only actions are teleport (via
 * {@link TeleportService#teleport}, same as {@code /home}) and page change.
 */
public class HomesMenus implements Listener, LifecycleComponent {

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

    public boolean available(Player player) {
        return config.guiEnabled() && player.hasPermission(PERMISSION);
    }

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

        // Added after opening: opening over an existing menu fires a close event that would remove this entry.
        open.put(player.getUniqueId(), menu);
    }

    /** Also called on shutdown and when a reload turns the menu off. */
    public void closeAll() {
        for (UUID id : List.copyOf(open.keySet())) {
            Player player = Bukkit.getPlayer(id);

            if (player != null) {
                player.closeInventory();
            }
        }

        open.clear();
    }

    public void closeAllIfDisabled() {
        if (!config.guiEnabled()) {
            closeAll();
        }
    }

    // Highest, so a plugin that un-cancels at a lower priority can't let items be taken.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomesMenu)) {
            return;
        }

        // Cancel on holder alone, so a missing or stale tracking entry can never let an item out
        // (shift-click, number-key swaps and double-click collection move items without clicking a menu slot).
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
            // A tick later: swapping or closing an inventory inside its own click event can desync the cursor.
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
        open.remove(event.getPlayer().getUniqueId());
    }

    private HomesMenu trackedMenu(HumanEntity who, Inventory top) {
        HomesMenu menu = open.get(who.getUniqueId());

        return menu != null && top.getHolder() == menu ? menu : null;
    }

    private void turnPage(Player player, HomesMenu menu, int page) {
        render(player, menu, homeManager.homesOf(player), page);
    }

    private void launch(Player player, HomesMenu menu, HomesMenu.Entry entry) {
        if (!player.isOnline()) {
            return;
        }

        if (open.get(player.getUniqueId()) == menu) {
            player.closeInventory();
        }

        // The menu is a shortcut for /home, so it must not bypass that permission.
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

        // ...nor the world rules.
        if (!worldRules.teleportAllowed(player, home.world())) {
            messages.send(player, "teleport.world-disabled",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("world", WorldNames.displayName(messages, home.world())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        teleportService.teleport(player, home);
    }

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

    /** Plain head with no skull owner: setting one makes the server fetch a profile. */
    private ItemStack sharedItem(PlayerHomes.Received hint) {
        TagResolver[] placeholders = {
                Placeholder.unparsed("owner", hint.ownerName()),
                Placeholder.unparsed("name", hint.home())
        };

        return named(Material.PLAYER_HEAD, messages.legacyItem("gui.shared-name", placeholders),
                messages.legacyItemLines("gui.shared-lore", placeholders));
    }

    /** Barrier for unloaded worlds; clicking it reports the orphaned home via the normal teleport path. */
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

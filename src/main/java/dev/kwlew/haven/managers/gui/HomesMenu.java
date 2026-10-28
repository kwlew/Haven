package dev.kwlew.haven.managers.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder that identifies Haven's menu (no title matching). Stores home names, not {@code Home}s, so a click re-reads
 * and reports deleted or renamed homes.
 */
final class HomesMenu implements InventoryHolder {

    private final int rows;

    private Inventory inventory;
    private int page;
    private Entry[] entries = new Entry[0];
    private boolean used;

    HomesMenu(int rows) {
        this.rows = rows;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    int rows() {
        return rows;
    }

    int page() {
        return page;
    }

    void page(int page) {
        this.page = page;
    }

    /** True for the first caller only: a double-click delivers two events before the menu closes. */
    boolean claim() {
        if (used) {
            return false;
        }

        used = true;
        return true;
    }

    void entries(Entry[] entries) {
        this.entries = entries;
    }

    Entry homeAt(int slot) {
        int index = MenuLayout.indexFor(slot, rows);

        return index >= 0 && index < entries.length ? entries[index] : null;
    }

    /** {@code owner} is null for the player's own home; {@code ownerName} comes from a hint and is display-only. */
    record Entry(String name, UUID owner, String ownerName) {

        static Entry own(String name) {
            return new Entry(name, null, null);
        }

        static Entry shared(String name, UUID owner, String ownerName) {
            return new Entry(name, owner, ownerName);
        }

        boolean isShared() {
            return owner != null;
        }
    }
}

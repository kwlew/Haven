package dev.kwlew.haven.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marks an inventory as Haven's homes menu, and carries what a click needs to know about it.
 * <p>
 * Being the inventory's holder is what identifies it: nothing else can construct one, so there is
 * no title-matching that another plugin's chest could imitate.
 * <p>
 * The menu stores home <em>names</em> (and for a shared home, whose it is), not {@code Home} objects. A click re-reads the player's
 * homes, so a home deleted or renamed while the menu was open is reported instead of teleporting
 * to stale coordinates.
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

    /**
     * Claims the one teleport this menu may launch. A double-click delivers two click events
     * before the menu has closed, and the second must not start a second teleport.
     *
     * @return true for the first caller only
     */
    boolean claim() {
        if (used) {
            return false;
        }

        used = true;
        return true;
    }

    /** Replaces the homes for the page currently shown, indexed by position on the page. */
    void entries(Entry[] entries) {
        this.entries = entries;
    }

    /** The home at an inventory slot, or {@code null} when the slot holds no home. */
    Entry homeAt(int slot) {
        int index = MenuLayout.indexFor(slot, rows);

        return index >= 0 && index < entries.length ? entries[index] : null;
    }

    /**
     * What a slot launches. {@code owner} is {@code null} for the player's own home; otherwise the
     * home belongs to that player and was shared with the viewer. The name is from the viewer's
     * hint, so it is for display only - the id is what identifies the owner.
     */
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

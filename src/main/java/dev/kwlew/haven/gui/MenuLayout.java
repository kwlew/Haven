package dev.kwlew.haven.gui;

/**
 * Slot arithmetic for the homes menu, kept free of Bukkit so it can be unit tested.
 * <p>
 * A chest is nine columns wide. The outermost ring is a border; homes fill the seven columns
 * inside it, one row at a time. The bottom row is border too, except for three control slots -
 * previous page, the info item, next page - that sit in it.
 */
final class MenuLayout {

    static final int COLUMNS = 9;
    static final int PER_ROW = 7;
    static final int MIN_ROWS = 3;
    static final int MAX_ROWS = 6;

    private MenuLayout() {}

    /**
     * Rows needed for {@code homeCount} homes: a border row top and bottom plus as many inner
     * rows as the homes need. Clamped so the chest is never smaller than a single inner row or
     * larger than the six rows a client can show.
     */
    static int rowsFor(int homeCount) {
        int inner = (Math.max(0, homeCount) + PER_ROW - 1) / PER_ROW;

        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, 2 + inner));
    }

    /** Homes one page holds in a chest of {@code rows} rows. */
    static int capacity(int rows) {
        return (rows - 2) * PER_ROW;
    }

    /** Always at least one page, so an empty menu still reads "page 1/1". */
    static int pageCount(int homeCount, int rows) {
        int capacity = capacity(rows);

        return Math.max(1, (Math.max(0, homeCount) + capacity - 1) / capacity);
    }

    /** Clamps a requested page into range, for when homes were removed while the menu was open. */
    static int clampPage(int page, int homeCount, int rows) {
        return Math.max(0, Math.min(page, pageCount(homeCount, rows) - 1));
    }

    /** Inventory slot for the {@code index}-th home on a page (0-based). */
    static int slotFor(int index) {
        return (1 + index / PER_ROW) * COLUMNS + 1 + index % PER_ROW;
    }

    /**
     * The home index on the page for an inventory slot, or -1 for a border, control or
     * out-of-range slot. Inverse of {@link #slotFor}.
     */
    static int indexFor(int slot, int rows) {
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;

        if (slot < 0 || row < 1 || row > rows - 2 || column < 1 || column > PER_ROW) {
            return -1;
        }

        return (row - 1) * PER_ROW + (column - 1);
    }

    static int previousSlot(int rows) {
        return (rows - 1) * COLUMNS + 1;
    }

    static int infoSlot(int rows) {
        return (rows - 1) * COLUMNS + 4;
    }

    static int nextSlot(int rows) {
        return (rows - 1) * COLUMNS + 7;
    }
}

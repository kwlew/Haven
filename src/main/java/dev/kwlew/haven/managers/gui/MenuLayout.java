package dev.kwlew.haven.managers.gui;

/**
 * Slot arithmetic for the homes menu, free of Bukkit for testing. Nine columns; a border ring, homes in the seven
 * inner columns, and previous/info/next controls in the bottom row.
 */
final class MenuLayout {

    static final int COLUMNS = 9;
    static final int PER_ROW = 7;
    static final int MIN_ROWS = 3;
    static final int MAX_ROWS = 6;

    private MenuLayout() {}

    static int rowsFor(int homeCount) {
        int inner = (Math.max(0, homeCount) + PER_ROW - 1) / PER_ROW;

        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, 2 + inner));
    }

    static int capacity(int rows) {
        return (rows - 2) * PER_ROW;
    }

    static int pageCount(int homeCount, int rows) {
        int capacity = capacity(rows);

        return Math.max(1, (Math.max(0, homeCount) + capacity - 1) / capacity);
    }

    static int clampPage(int page, int homeCount, int rows) {
        return Math.max(0, Math.min(page, pageCount(homeCount, rows) - 1));
    }

    static int slotFor(int index) {
        return (1 + index / PER_ROW) * COLUMNS + 1 + index % PER_ROW;
    }

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

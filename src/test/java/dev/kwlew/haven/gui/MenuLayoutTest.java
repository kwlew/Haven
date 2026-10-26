package dev.kwlew.haven.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLayoutTest {

    @Test
    void rowsGrowWithHomesAndAreClamped() {
        assertEquals(3, MenuLayout.rowsFor(0));
        assertEquals(3, MenuLayout.rowsFor(1));
        assertEquals(3, MenuLayout.rowsFor(7));
        assertEquals(4, MenuLayout.rowsFor(8));
        assertEquals(4, MenuLayout.rowsFor(14));
        assertEquals(5, MenuLayout.rowsFor(15));
        assertEquals(6, MenuLayout.rowsFor(22));
        assertEquals(6, MenuLayout.rowsFor(28));
        assertEquals(6, MenuLayout.rowsFor(500));
    }

    @Test
    void capacityIsSevenPerInnerRow() {
        assertEquals(7, MenuLayout.capacity(3));
        assertEquals(28, MenuLayout.capacity(6));
    }

    @Test
    void pagesCoverEveryHome() {
        assertEquals(1, MenuLayout.pageCount(0, 3));
        assertEquals(1, MenuLayout.pageCount(28, 6));
        assertEquals(2, MenuLayout.pageCount(29, 6));
        assertEquals(3, MenuLayout.pageCount(57, 6));
        assertEquals(1, MenuLayout.pageCount(5, 3));
    }

    @Test
    void clampsPagesIntoRange() {
        assertEquals(0, MenuLayout.clampPage(-1, 40, 6));
        assertEquals(1, MenuLayout.clampPage(5, 40, 6));
        assertEquals(0, MenuLayout.clampPage(3, 10, 6));
    }

    @Test
    void homesFillTheInnerColumnsRowByRow() {
        assertEquals(10, MenuLayout.slotFor(0));
        assertEquals(16, MenuLayout.slotFor(6));
        assertEquals(19, MenuLayout.slotFor(7));
        assertEquals(43, MenuLayout.slotFor(27));
    }

    @Test
    void homeSlotsStayInsideTheBorderForEverySize() {
        for (int rows = MenuLayout.MIN_ROWS; rows <= MenuLayout.MAX_ROWS; rows++) {
            Set<Integer> seen = new HashSet<>();

            for (int i = 0; i < MenuLayout.capacity(rows); i++) {
                int slot = MenuLayout.slotFor(i);

                assertTrue(seen.add(slot), "duplicate slot " + slot);
                assertTrue(slot / 9 >= 1 && slot / 9 <= rows - 2, "row of " + slot);
                assertTrue(slot % 9 >= 1 && slot % 9 <= 7, "column of " + slot);
                assertEquals(i, MenuLayout.indexFor(slot, rows));
            }
        }
    }

    @Test
    void borderAndControlSlotsAreNotHomes() {
        assertEquals(-1, MenuLayout.indexFor(0, 6));
        assertEquals(-1, MenuLayout.indexFor(9, 6));
        assertEquals(-1, MenuLayout.indexFor(17, 6));
        assertEquals(-1, MenuLayout.indexFor(46, 6));
        assertEquals(-1, MenuLayout.indexFor(-999, 6));
        assertEquals(-1, MenuLayout.indexFor(54, 6));
        // A slot inside a taller chest is the bottom border in a shorter one.
        assertEquals(-1, MenuLayout.indexFor(19, 3));
    }

    @Test
    void controlsSitInTheBottomRow() {
        assertEquals(46, MenuLayout.previousSlot(6));
        assertEquals(49, MenuLayout.infoSlot(6));
        assertEquals(52, MenuLayout.nextSlot(6));
        assertEquals(19, MenuLayout.previousSlot(3));
        assertEquals(22, MenuLayout.infoSlot(3));
        assertEquals(25, MenuLayout.nextSlot(3));
    }
}

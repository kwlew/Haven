package dev.kwlew.haven.home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRulesTest {

    private static Home home(String name, String world) {
        return new Home(name, world, 0, 64, 0, 0f, 0f, 0L);
    }

    private static final List<Home> HOMES = List.of(
            home("base", "world"),
            home("mine", "world_nether"),
            home("farm", "world"));

    @Test
    void newHomeUnderCapIsAllowed() {
        assertFalse(WorldRules.exceedsCap(OptionalInt.of(2), HOMES, "fresh", "world_nether"));
    }

    @Test
    void newHomeAtCapIsBlocked() {
        assertTrue(WorldRules.exceedsCap(OptionalInt.of(2), HOMES, "fresh", "world"));
        assertTrue(WorldRules.exceedsCap(OptionalInt.of(1), HOMES, "fresh", "world_nether"));
    }

    @Test
    void movingIntoAFullWorldIsBlocked() {
        // "mine" lives in the nether; moving it to the overworld adds a third home there.
        assertTrue(WorldRules.exceedsCap(OptionalInt.of(2), HOMES, "mine", "world"));
    }

    @Test
    void movingIntoAWorldWithRoomIsAllowed() {
        assertFalse(WorldRules.exceedsCap(OptionalInt.of(3), HOMES, "mine", "world"));
    }

    @Test
    void overwritingWithinTheSameWorldIsNeverBlocked() {
        assertFalse(WorldRules.exceedsCap(OptionalInt.of(2), HOMES, "base", "world"));
        assertFalse(WorldRules.exceedsCap(OptionalInt.of(0), HOMES, "base", "world"));
    }

    @Test
    void unlistedWorldIsUncapped() {
        assertFalse(WorldRules.exceedsCap(OptionalInt.empty(), HOMES, "fresh", "world"));
    }

    @Test
    void zeroCapForbidsAnyNewHome() {
        assertTrue(WorldRules.exceedsCap(OptionalInt.of(0), HOMES, "fresh", "the_end"));
    }

    @Test
    void countExcludesTheHomeBeingMoved() {
        assertEquals(2, WorldRules.countIn(HOMES, "fresh", "world"));
        assertEquals(1, WorldRules.countIn(HOMES, "base", "world"));
        assertEquals(0, WorldRules.countIn(HOMES, "mine", "world_nether"));
    }
}

package dev.kwlew.haven.home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHomesTest {

    private static Home home(String name, long created) {
        return new Home(name, "world", 1.5, 64, -2.5, 90f, 15f, created);
    }

    private static PlayerHomes homes(Home... homes) {
        PlayerHomes result = new PlayerHomes(UUID.randomUUID(), "Steve");
        for (Home home : homes) {
            result.put(home);
        }
        return result;
    }

    @Test
    void renameKeepsEverythingButTheName() {
        PlayerHomes homes = homes(home("base", 1234L));

        assertTrue(homes.rename("base", "castle"));

        assertNull(homes.get("base"));
        assertEquals(home("castle", 1234L), homes.get("castle"));
        assertEquals(1, homes.count());
    }

    @Test
    void renameKeepsInsertionOrder() {
        PlayerHomes homes = homes(home("a", 1L), home("b", 2L), home("c", 3L));

        assertTrue(homes.rename("b", "z"));

        assertEquals(List.of("a", "z", "c"), homes.names());
    }

    @Test
    void renameIsCaseInsensitive() {
        PlayerHomes homes = homes(home("base", 1L));

        assertTrue(homes.rename("BASE", "Castle"));

        assertTrue(homes.has("castle"));
    }

    @Test
    void renameFailsWhenSourceIsMissing() {
        PlayerHomes homes = homes(home("base", 1L));

        assertFalse(homes.rename("missing", "castle"));

        assertEquals(List.of("base"), homes.names());
    }

    @Test
    void renameFailsWhenTargetExists() {
        PlayerHomes homes = homes(home("base", 1L), home("castle", 2L));

        assertFalse(homes.rename("base", "castle"));

        assertEquals(home("base", 1L), homes.get("base"));
        assertEquals(home("castle", 2L), homes.get("castle"));
    }

    @Test
    void renameKeepsWhoTheHomeIsSharedWith() {
        UUID friend = UUID.randomUUID();
        PlayerHomes homes = homes(home("base", 1L).withSharedWith(friend));

        assertTrue(homes.rename("base", "castle"));

        assertTrue(homes.get("castle").isSharedWith(friend));
    }

    @Test
    void receivedHintsReplaceByOwnerAndHomeAndReportChanges() {
        UUID owner = UUID.randomUUID();
        PlayerHomes homes = homes();

        assertTrue(homes.putReceived(new PlayerHomes.Received(owner, "Dave", "Castle")));
        assertFalse(homes.putReceived(new PlayerHomes.Received(owner, "Dave", "castle")));
        // A new owner name refreshes the existing hint instead of adding a second one.
        assertTrue(homes.putReceived(new PlayerHomes.Received(owner, "David", "castle")));

        assertEquals(List.of(new PlayerHomes.Received(owner, "David", "castle")), homes.received());
        assertTrue(homes.hasReceived(owner, "CASTLE"));
    }

    @Test
    void removingAHintReportsWhetherOneWasThere() {
        UUID owner = UUID.randomUUID();
        PlayerHomes homes = homes();
        homes.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));

        assertTrue(homes.removeReceived(owner, "castle"));
        assertFalse(homes.removeReceived(owner, "castle"));
        assertEquals(List.of(), homes.received());
    }

    @Test
    void receivedHintsAreListedByOwnerNameThenHome() {
        PlayerHomes homes = homes();
        UUID zed = UUID.randomUUID();
        UUID amy = UUID.randomUUID();
        homes.putReceived(new PlayerHomes.Received(zed, "zed", "a"));
        homes.putReceived(new PlayerHomes.Received(amy, "Amy", "z"));
        homes.putReceived(new PlayerHomes.Received(amy, "Amy", "b"));

        assertEquals(List.of("Amy:b", "Amy:z", "zed:a"), homes.received().stream()
                .map(hint -> hint.ownerName() + ":" + hint.home()).toList());
    }

    @Test
    void snapshotRoundTripKeepsHintsAndShares() {
        UUID friend = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        PlayerHomes homes = homes(home("base", 1L).withSharedWith(friend));
        homes.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));

        PlayerHomes restored = PlayerHomes.fromSnapshot(homes.snapshot());

        assertTrue(restored.get("base").isSharedWith(friend));
        assertEquals(homes.received(), restored.received());
    }

    @Test
    void aHintWithoutAnOwnerNameFallsBackToTheId() {
        UUID owner = UUID.randomUUID();

        assertEquals(owner.toString(), new PlayerHomes.Received(owner, null, "castle").ownerName());
    }
}

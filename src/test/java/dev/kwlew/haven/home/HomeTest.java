package dev.kwlew.haven.home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTest {

    private static final Home BASE = new Home("base", "world", 1.5, 64, -2.5, 90f, 15f, 1234L);

    @Test
    void aNewHomeIsSharedWithNobody() {
        assertTrue(BASE.sharedWith().isEmpty());
    }

    @Test
    void sharingReturnsACopyAndLeavesTheOriginalAlone() {
        UUID friend = UUID.randomUUID();

        Home shared = BASE.withSharedWith(friend);

        assertTrue(shared.isSharedWith(friend));
        assertFalse(BASE.isSharedWith(friend));
        assertEquals(BASE.world(), shared.world());
        assertEquals(BASE.created(), shared.created());
    }

    @Test
    void sharingTwiceOrUnsharingAStrangerChangesNothing() {
        UUID friend = UUID.randomUUID();
        Home shared = BASE.withSharedWith(friend);

        assertSame(shared, shared.withSharedWith(friend));
        assertSame(BASE, BASE.withoutSharedWith(friend));
    }

    @Test
    void unsharingRemovesOnlyThatPlayerAndKeepsOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Home shared = BASE.withSharedWith(a).withSharedWith(b).withSharedWith(c);

        assertEquals(List.of(a, c), List.copyOf(shared.withoutSharedWith(b).sharedWith()));
    }

    @Test
    void theSharedSetCannotBeModifiedFromOutside() {
        Home shared = BASE.withSharedWith(UUID.randomUUID());

        assertThrows(UnsupportedOperationException.class, () -> shared.sharedWith().add(UUID.randomUUID()));
    }

    @Test
    void sharesDoNotChangeWhenTheSourceCollectionDoes() {
        UUID friend = UUID.randomUUID();
        java.util.Set<UUID> source = new java.util.HashSet<>(Set.of(friend));
        Home home = new Home("base", "world", 0, 0, 0, 0, 0, 0, source);

        source.clear();

        assertTrue(home.isSharedWith(friend));
    }

    @Test
    void renamingKeepsEverythingElse() {
        UUID friend = UUID.randomUUID();

        Home renamed = BASE.withSharedWith(friend).renamed("castle");

        assertEquals("castle", renamed.name());
        assertTrue(renamed.isSharedWith(friend));
        assertEquals(BASE.x(), renamed.x());
        assertEquals(BASE.created(), renamed.created());
    }

    @Test
    void movingAHomeKeepsItsNameAndSharesButTakesTheNewSpot() {
        UUID friend = UUID.randomUUID();
        Home moved = new Home("base", "world_nether", 9, 70, 9, 0f, 0f, 5678L);

        Home result = BASE.withSharedWith(friend).movedTo(moved);

        assertEquals("base", result.name());
        assertTrue(result.isSharedWith(friend));
        assertEquals("world_nether", result.world());
        assertEquals(9, result.x());
        assertEquals(5678L, result.created());
    }

    @Test
    void homeNamesCanNeverContainTheOwnerSeparator() {
        assertFalse(Home.isValidName("owner:home"));
    }
}

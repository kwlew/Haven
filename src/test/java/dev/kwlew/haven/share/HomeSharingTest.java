package dev.kwlew.haven.share;

import dev.kwlew.haven.home.PlayerHomes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSharingTest {

    private final UUID owner = UUID.randomUUID();
    private final PlayerHomes mine = new PlayerHomes(UUID.randomUUID(), "Steve");

    @Test
    void aStaleHintIsPrunedWhenTheOwnersDataNoLongerSharesTheHome() {
        mine.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));

        assertTrue(HomeSharing.reconcile(mine, owner, "Dave", "castle", false));

        assertEquals(List.of(), mine.received());
    }

    @Test
    void pruningAHintThatIsAlreadyGoneChangesNothing() {
        assertFalse(HomeSharing.reconcile(mine, owner, "Dave", "castle", false));
    }

    @Test
    void aMissingHintIsAddedWhenTheOwnersDataSharesTheHome() {
        assertTrue(HomeSharing.reconcile(mine, owner, "Dave", "castle", true));

        assertEquals(List.of(new PlayerHomes.Received(owner, "Dave", "castle")), mine.received());
    }

    @Test
    void aCurrentHintIsLeftAloneSoNothingIsSavedNeedlessly() {
        mine.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));

        assertFalse(HomeSharing.reconcile(mine, owner, "Dave", "castle", true));
    }

    @Test
    void aRenamedOwnerIsRefreshedInPlace() {
        mine.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));

        assertTrue(HomeSharing.reconcile(mine, owner, "David", "castle", true));

        assertEquals(List.of(new PlayerHomes.Received(owner, "David", "castle")), mine.received());
    }

    @Test
    void onlyTheNamedHomeOfTheNamedOwnerIsPruned() {
        UUID other = UUID.randomUUID();
        mine.putReceived(new PlayerHomes.Received(owner, "Dave", "castle"));
        mine.putReceived(new PlayerHomes.Received(owner, "Dave", "farm"));
        mine.putReceived(new PlayerHomes.Received(other, "Eve", "castle"));

        HomeSharing.reconcile(mine, owner, "Dave", "castle", false);

        assertEquals(2, mine.received().size());
        assertTrue(mine.hasReceived(owner, "farm"));
        assertTrue(mine.hasReceived(other, "castle"));
    }
}

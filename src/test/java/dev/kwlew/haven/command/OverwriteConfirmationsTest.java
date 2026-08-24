package dev.kwlew.haven.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverwriteConfirmationsTest {

    private static final int TIMEOUT_SECONDS = 15;
    private static final long START = 1_000_000L;

    private final OverwriteConfirmations confirmations = new OverwriteConfirmations(() -> TIMEOUT_SECONDS);
    private final UUID player = UUID.randomUUID();

    @Test
    void firstCallPromptsRatherThanConfirming() {
        assertFalse(confirmations.confirmAt(player, "base", START));
    }

    @Test
    void repeatingTheSameHomeConfirms() {
        confirmations.confirmAt(player, "base", START);

        assertTrue(confirmations.confirmAt(player, "base", START + 1000L));
    }

    /**
     * The reason a pending entry stores the home name: otherwise "/sethome base" then
     * "/sethome spawn" would read as a confirmation and move the wrong home.
     */
    @Test
    void aDifferentHomeDoesNotConfirmThePendingOne() {
        confirmations.confirmAt(player, "base", START);

        assertFalse(confirmations.confirmAt(player, "spawn", START + 1000L),
                "a different home must prompt, not confirm");
    }

    @Test
    void switchingHomesReplacesThePendingRequest() {
        confirmations.confirmAt(player, "base", START);
        confirmations.confirmAt(player, "spawn", START + 1000L);

        assertFalse(confirmations.confirmAt(player, "base", START + 2000L),
                "the superseded home should no longer be pending");
        assertTrue(confirmations.confirmAt(player, "base", START + 3000L));
    }

    @Test
    void expiredRequestsPromptAgain() {
        confirmations.confirmAt(player, "base", START);

        long afterTimeout = START + (TIMEOUT_SECONDS * 1000L) + 1;

        assertFalse(confirmations.confirmAt(player, "base", afterTimeout));
    }

    @Test
    void confirmingClearsThePendingRequest() {
        confirmations.confirmAt(player, "base", START);
        assertTrue(confirmations.confirmAt(player, "base", START + 1000L));

        assertFalse(confirmations.confirmAt(player, "base", START + 2000L),
                "a confirmed request must not stay armed");
    }

    @Test
    void clearRemovesAPendingRequest() {
        confirmations.confirmAt(player, "base", START);
        confirmations.clear(player);

        assertFalse(confirmations.confirmAt(player, "base", START + 1000L));
    }

    @Test
    void playersAreTrackedIndependently() {
        UUID other = UUID.randomUUID();

        confirmations.confirmAt(player, "base", START);

        assertFalse(confirmations.confirmAt(other, "base", START + 1000L),
                "one player's pending request must not confirm another's");
    }
}

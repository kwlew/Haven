package dev.kwlew.haven.home;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeLimitsTest {

    private static final int FALLBACK = 3;

    @Test
    void fallsBackWhenNoLimitNodeIsGranted() {
        assertEquals(FALLBACK, HomeLimits.resolve(Map.of("haven.home", true), FALLBACK));
    }

    @Test
    void usesTheHighestGrantedNode() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("haven.homes.3", true);
        permissions.put("haven.homes.10", true);
        permissions.put("haven.homes.5", true);

        assertEquals(10, HomeLimits.resolve(permissions, FALLBACK));
    }

    @Test
    void unlimitedBeatsAnyNumericNode() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("haven.homes.5", true);
        permissions.put("haven.homes.unlimited", true);

        assertEquals(HomeLimits.UNLIMITED, HomeLimits.resolve(permissions, FALLBACK));
    }

    /**
     * Denied nodes are present in a player's effective permissions. Counting them would restore a
     * cap an admin explicitly revoked.
     */
    @Test
    void ignoresNegatedNodes() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("haven.homes.5", true);
        permissions.put("haven.homes.50", false);

        assertEquals(5, HomeLimits.resolve(permissions, FALLBACK));
    }

    @Test
    void ignoresNegatedUnlimited() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("haven.homes.unlimited", false);
        permissions.put("haven.homes.4", true);

        assertEquals(4, HomeLimits.resolve(permissions, FALLBACK));
    }

    /**
     * A typo'd LuckPerms node must not throw out of a command handler.
     */
    @Test
    void ignoresUnparseableNodes() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("haven.homes.foo", true);
        permissions.put("haven.homes.", true);
        permissions.put("haven.homes.2", true);

        assertEquals(2, HomeLimits.resolve(permissions, FALLBACK));
    }

    @Test
    void unrelatedNodesDoNotInterfere() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("someotherplugin.homes.99", true);
        permissions.put("haven.home", true);

        assertEquals(FALLBACK, HomeLimits.resolve(permissions, FALLBACK));
    }

    @Test
    void zeroIsAGrantedLimitNotAnAbsentOne() {
        assertEquals(0, HomeLimits.resolve(Map.of("haven.homes.0", true), FALLBACK));
    }
}

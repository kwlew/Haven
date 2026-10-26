package dev.kwlew.haven.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerLookupTest {

    private static final UUID ONLINE_ID = UUID.randomUUID();
    private static final UUID OFFLINE_ID = UUID.randomUUID();

    /** A bare-bones stand-in answering only the two calls the lookup makes. */
    private static <T> T stub(Class<T> type, UUID id, String name) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
    }

    @Test
    void onlinePlayerWinsOverTheCache() {
        PlayerLookup lookup = new PlayerLookup(
                name -> stub(Player.class, ONLINE_ID, "Alice"),
                name -> stub(OfflinePlayer.class, OFFLINE_ID, "alice"));

        assertEquals(new PlayerLookup.Target(ONLINE_ID, "Alice"), lookup.find("alice"));
    }

    @Test
    void offlinePlayerResolvesFromTheCacheWithTheirCanonicalName() {
        PlayerLookup lookup = new PlayerLookup(
                name -> null, name -> stub(OfflinePlayer.class, OFFLINE_ID, "Bob"));

        assertEquals(new PlayerLookup.Target(OFFLINE_ID, "Bob"), lookup.find("bob"));
    }

    @Test
    void unknownNameResolvesToNothing() {
        PlayerLookup lookup = new PlayerLookup(name -> null, name -> null);

        assertNull(lookup.find("nobody"));
    }

    @Test
    void cacheEntryWithoutANameIsRejected() {
        PlayerLookup lookup = new PlayerLookup(
                name -> null, name -> stub(OfflinePlayer.class, OFFLINE_ID, null));

        assertNull(lookup.find("ghost"));
    }
}

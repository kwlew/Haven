package dev.kwlew.haven.home;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One player's homes, held in memory while they're online.
 * <p>
 * <strong>Main-thread only once installed in the cache.</strong> The storage thread never touches
 * a live instance - it is handed an immutable {@link Snapshot} instead. Instances are also built
 * off-thread during pre-login, but only before anything else can observe them.
 */
public final class PlayerHomes {

    private final UUID owner;
    private final Map<String, Home> homes = new LinkedHashMap<>();

    private String lastKnownName;
    private long lastTeleport;

    public PlayerHomes(UUID owner, String lastKnownName) {
        this.owner = owner;
        this.lastKnownName = lastKnownName;
    }

    public UUID owner() {
        return owner;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void lastKnownName(String name) {
        this.lastKnownName = name;
    }

    public long lastTeleport() {
        return lastTeleport;
    }

    public void lastTeleport(long millis) {
        this.lastTeleport = millis;
    }

    public Home get(String name) {
        return homes.get(Home.normalize(name));
    }

    public boolean has(String name) {
        return homes.containsKey(Home.normalize(name));
    }

    public void put(Home home) {
        homes.put(home.name(), home);
    }

    public Home remove(String name) {
        return homes.remove(Home.normalize(name));
    }

    public int count() {
        return homes.size();
    }

    public Collection<Home> all() {
        return List.copyOf(homes.values());
    }

    public List<String> names() {
        return List.copyOf(homes.keySet());
    }

    /**
     * An immutable copy safe to hand to the storage thread.
     */
    public Snapshot snapshot() {
        return new Snapshot(owner, lastKnownName, lastTeleport, List.copyOf(homes.values()));
    }

    public static PlayerHomes fromSnapshot(Snapshot snapshot) {
        PlayerHomes restored = new PlayerHomes(snapshot.owner(), snapshot.lastKnownName());
        restored.lastTeleport = snapshot.lastTeleport();

        for (Home home : snapshot.homes()) {
            restored.homes.put(home.name(), home);
        }

        return restored;
    }

    public record Snapshot(UUID owner, String lastKnownName, long lastTeleport, List<Home> homes) {

        public Snapshot {
            homes = List.copyOf(homes);
        }
    }
}

package dev.kwlew.haven.managers.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * One player's homes plus hints about homes shared with them. Main-thread only once cached; storage gets an immutable
 * {@link Snapshot}.
 */
public final class PlayerHomes {

    private final UUID owner;
    private final Map<String, Home> homes = new LinkedHashMap<>();
    private final Map<String, Received> received = new LinkedHashMap<>();

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

    public boolean rename(String from, String to) {
        String oldName = Home.normalize(from);
        String newName = Home.normalize(to);

        if (!homes.containsKey(oldName) || homes.containsKey(newName)) {
            return false;
        }

        // Rebuilt to keep insertion order; a LinkedHashMap can't re-key an entry.
        Map<String, Home> renamed = new LinkedHashMap<>();
        for (Home home : homes.values()) {
            if (home.name().equals(oldName)) {
                home = home.renamed(newName);
            }
            renamed.put(home.name(), home);
        }

        homes.clear();
        homes.putAll(renamed);
        return true;
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
     * Hints copied from the owners' files; {@link Home#sharedWith} is the truth, so re-check before acting and call
     * {@link #removeReceived} if stale.
     */
    public List<Received> received() {
        List<Received> sorted = new ArrayList<>(received.values());
        sorted.sort(Received.DISPLAY_ORDER);

        return sorted;
    }

    public boolean hasReceived(UUID homeOwner, String home) {
        return received.containsKey(Received.key(homeOwner, home));
    }

    /** Returns whether anything changed, so callers can skip a save. */
    public boolean putReceived(Received hint) {
        return !hint.equals(received.put(hint.key(), hint));
    }

    public boolean removeReceived(UUID homeOwner, String home) {
        return received.remove(Received.key(homeOwner, home)) != null;
    }

    public Snapshot snapshot() {
        return new Snapshot(owner, lastKnownName, lastTeleport, List.copyOf(homes.values()),
                List.copyOf(received.values()));
    }

    public static PlayerHomes fromSnapshot(Snapshot snapshot) {
        PlayerHomes restored = new PlayerHomes(snapshot.owner(), snapshot.lastKnownName());
        restored.lastTeleport = snapshot.lastTeleport();

        for (Home home : snapshot.homes()) {
            restored.homes.put(home.name(), home);
        }

        for (Received hint : snapshot.received()) {
            restored.received.put(hint.key(), hint);
        }

        return restored;
    }

    /**
     * A pointer to someone else's shared home; carries no coordinates because only the owner's file may be teleported
     * to. {@code ownerName} is for display only and may be stale.
     */
    public record Received(UUID owner, String ownerName, String home) {

        // Owner id breaks ties between a rename's old and new names.
        static final Comparator<Received> DISPLAY_ORDER = Comparator
                .<Received, String>comparing(hint -> hint.ownerName.toLowerCase(Locale.ROOT))
                .thenComparing(Received::home)
                .thenComparing(Received::owner);

        public Received {
            home = Home.normalize(home);
            ownerName = ownerName == null || ownerName.isBlank() ? owner.toString() : ownerName;
        }

        String key() {
            return key(owner, home);
        }

        static String key(UUID owner, String home) {
            return owner + "/" + Home.normalize(home);
        }
    }

    public record Snapshot(UUID owner, String lastKnownName, long lastTeleport, List<Home> homes,
                           List<Received> received) {

        public Snapshot {
            homes = List.copyOf(homes);
            received = List.copyOf(received);
        }

        public Snapshot(UUID owner, String lastKnownName, long lastTeleport, List<Home> homes) {
            this(owner, lastKnownName, lastTeleport, homes, List.of());
        }
    }
}

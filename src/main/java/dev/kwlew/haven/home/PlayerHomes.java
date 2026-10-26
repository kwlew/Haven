package dev.kwlew.haven.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * One player's homes, held in memory while they're online, plus the hints about homes other players
 * have shared with them.
 * <p>
 * <strong>Main-thread only once installed in the cache.</strong> The storage thread never touches
 * a live instance - it is handed an immutable {@link Snapshot} instead. Instances are also built
 * off-thread during pre-login, but only before anything else can observe them.
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

    /**
     * Renames a home in place, keeping its position in the list. Returns {@code false} without
     * changing anything if {@code from} is missing or {@code to} is already taken.
     */
    public boolean rename(String from, String to) {
        String oldName = Home.normalize(from);
        String newName = Home.normalize(to);

        if (!homes.containsKey(oldName) || homes.containsKey(newName)) {
            return false;
        }

        // A LinkedHashMap can't re-key an entry, so rebuild it to keep the insertion order.
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
     * Homes other players have shared with this one, ordered for display: by owner name, then home.
     * <p>
     * These are <em>hints</em>, copied from the owner's file so a list or tab-completion never has
     * to read someone else's data. The owner's {@link Home#sharedWith} is the truth, so anything
     * that acts on a hint must re-check it there and call {@link #removeReceived} if it is stale.
     */
    public List<Received> received() {
        List<Received> sorted = new ArrayList<>(received.values());
        sorted.sort(Received.DISPLAY_ORDER);

        return sorted;
    }

    public boolean hasReceived(UUID homeOwner, String home) {
        return received.containsKey(Received.key(homeOwner, home));
    }

    /**
     * Records a hint, replacing any earlier one for the same owner and home (which is how a changed
     * owner name is refreshed). Returns whether anything changed, so callers can skip a save.
     */
    public boolean putReceived(Received hint) {
        return !hint.equals(received.put(hint.key(), hint));
    }

    /** Drops a hint. Returns whether one was there. */
    public boolean removeReceived(UUID homeOwner, String home) {
        return received.remove(Received.key(homeOwner, home)) != null;
    }

    /**
     * An immutable copy safe to hand to the storage thread.
     */
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
     * A pointer to a home owned by someone else that has been shared with this player.
     * <p>
     * Deliberately carries no coordinates: the owner may have moved the home since, and the owner's
     * file is the only copy that may be teleported to.
     *
     * @param owner     whose home it is
     * @param ownerName their last known name, only for display and completion; may be out of date
     * @param home      the home's name in the owner's list
     */
    public record Received(UUID owner, String ownerName, String home) {

        /** By owner name, then home name, ignoring case; ids break ties between a rename's old and new names. */
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

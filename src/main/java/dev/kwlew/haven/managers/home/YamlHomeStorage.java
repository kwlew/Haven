package dev.kwlew.haven.managers.home;

import dev.kwlew.haven.kernel.Inject;
import dev.kwlew.haven.managers.config.HavenConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One file per player under {@code plugins/Haven/homes/}. I/O runs on a private FIFO executor, not the Bukkit
 * scheduler, so a save queued just before disable is not dropped and a fast reconnect observes the quit save.
 */
public class YamlHomeStorage implements HomeStorage {

    // v2 added shared-with and received; a v1 file loads as is, and older builds refuse the v2 file written back.
    private static final int SCHEMA_VERSION = 2;
    private static final int OLDEST_SCHEMA_VERSION = 1;

    private final Logger logger;
    private final IntSupplier shutdownTimeoutSeconds;
    private final File directory;
    private final ExecutorService executor;
    // Latest unwritten snapshot per owner; presence means a write task is already queued.
    private final Map<UUID, PlayerHomes.Snapshot> pendingSaves = new ConcurrentHashMap<>();

    private volatile boolean closing;

    @Inject
    public YamlHomeStorage(JavaPlugin plugin, HavenConfig config) {
        this(plugin.getDataFolder().toPath().resolve("homes"), plugin.getLogger(),
                config::shutdownTimeoutSeconds, newIoExecutor());
    }

    YamlHomeStorage(Path directory, Logger logger, IntSupplier shutdownTimeoutSeconds,
                    ExecutorService executor) {
        this.logger = logger;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        this.directory = directory.toFile();
        this.executor = executor;
    }

    private static ExecutorService newIoExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Haven-IO");
            // Non-daemon: pending writes must finish even if the JVM is winding down.
            thread.setDaemon(false);
            return thread;
        });
    }

    @Override
    public void init() {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create home storage directory: " + directory);
        }
    }

    @Override
    public PlayerHomes.Snapshot load(UUID owner, String lastKnownName) {
        // Shares the save queue so a reconnect right after quitting sees the quit save.
        Future<PlayerHomes.Snapshot> read;
        try {
            read = executor.submit(() -> loadFromDisk(owner, lastKnownName));
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Storage is closing; cannot load homes for " + owner, e);
        }

        try {
            return read.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading homes for " + owner, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not load homes for " + owner, e.getCause());
        }
    }

    @Override
    public CompletableFuture<PlayerHomes.Snapshot> loadAsync(UUID owner, String lastKnownName) {
        CompletableFuture<PlayerHomes.Snapshot> result = new CompletableFuture<>();

        try {
            executor.execute(() -> {
                try {
                    result.complete(loadFromDisk(owner, lastKnownName));
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(
                    new IllegalStateException("Storage is closing; cannot load homes for " + owner, e));
        }

        return result;
    }

    private PlayerHomes.Snapshot loadFromDisk(UUID owner, String lastKnownName) {
        YamlConfiguration yaml = readOrRecover(owner);

        if (yaml == null) {
            return new PlayerHomes.Snapshot(owner, lastKnownName, 0L, List.of());
        }

        Object schemaValue = yaml.get("schema-version");
        if (!(schemaValue instanceof Number schemaNumber)) {
            throw new IllegalStateException("Home file for " + owner + " has no valid schema version");
        }
        double schema = schemaNumber.doubleValue();
        if (schema < OLDEST_SCHEMA_VERSION || schema > SCHEMA_VERSION || schema != Math.rint(schema)) {
            throw new IllegalStateException("Home file for " + owner + " uses schema v" + schema
                    + "; this build supports v" + OLDEST_SCHEMA_VERSION + " to v" + SCHEMA_VERSION);
        }

        // The caller's name is current; the stored one may be a rename ago.
        String name = lastKnownName != null ? lastKnownName : yaml.getString("last-known-name");

        return new PlayerHomes.Snapshot(
                owner,
                name,
                yaml.getLong("last-teleport", 0L),
                readHomes(owner, yaml),
                readReceived(owner, yaml)
        );
    }

    private List<Home> readHomes(UUID owner, YamlConfiguration yaml) {
        ConfigurationSection homesSection = yaml.getConfigurationSection("homes");
        List<Home> homes = new ArrayList<>();

        if (homesSection == null) {
            if (yaml.isSet("homes")) {
                throw new IllegalStateException("Homes for " + owner + " are not a YAML section");
            }
            return homes;
        }

        Set<String> seen = new HashSet<>();
        for (String key : homesSection.getKeys(false)) {
            ConfigurationSection entry = homesSection.getConfigurationSection(key);
            String name = Home.normalize(key);

            if (entry == null) {
                throw new IllegalStateException("Home '" + key + "' for " + owner + " is not a section");
            }

            String world = entry.getString("world");

            if (!Home.isValidName(name)) {
                throw new IllegalStateException("Invalid home name '" + key + "' for " + owner);
            }
            if (!seen.add(name)) {
                throw new IllegalStateException("Duplicate home name '" + key + "' for " + owner);
            }

            if (world == null || world.isBlank()) {
                throw new IllegalStateException("Home '" + name + "' for " + owner + " has no world");
            }

            double x = coordinate(entry, "x", name, owner);
            double y = coordinate(entry, "y", name, owner);
            double z = coordinate(entry, "z", name, owner);
            double yaw = coordinate(entry, "yaw", name, owner);
            double pitch = coordinate(entry, "pitch", name, owner);
            if (!Float.isFinite((float) yaw) || !Float.isFinite((float) pitch)) {
                throw new IllegalStateException("Home '" + name + "' for " + owner
                        + " has an invalid rotation");
            }

            homes.add(new Home(
                    name,
                    world,
                    x, y, z,
                    (float) yaw,
                    (float) pitch,
                    entry.getLong("created"),
                    readSharedWith(entry, name, owner)
            ));
        }

        return homes;
    }

    /** Strict, unlike {@link #readReceived}: silently dropping an entry would revoke a share nobody asked to revoke. */
    private Set<UUID> readSharedWith(ConfigurationSection entry, String name, UUID owner) {
        if (!entry.isSet("shared-with")) {
            return Set.of();
        }
        if (!entry.isList("shared-with")) {
            throw new IllegalStateException("Home '" + name + "' for " + owner
                    + " has a shared-with that is not a list");
        }

        Set<UUID> shared = new LinkedHashSet<>();
        for (Object raw : entry.getList("shared-with")) {
            try {
                shared.add(UUID.fromString(String.valueOf(raw)));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Home '" + name + "' for " + owner
                        + " is shared with an invalid player id '" + raw + "'", e);
            }
        }

        return shared;
    }

    /**
     * Lenient: a denormalised copy that is re-checked on use, so a damaged entry is skipped rather than blocking
     * login.
     */
    private List<PlayerHomes.Received> readReceived(UUID owner, YamlConfiguration yaml) {
        List<PlayerHomes.Received> received = new ArrayList<>();

        for (Map<?, ?> entry : yaml.getMapList("received")) {
            try {
                received.add(new PlayerHomes.Received(
                        UUID.fromString(String.valueOf(entry.get("owner"))),
                        entry.get("owner-name") == null ? null : String.valueOf(entry.get("owner-name")),
                        validHomeName(entry.get("home"))));
            } catch (IllegalArgumentException e) {
                logger.warning("Ignoring a malformed received-home entry for " + owner + ": " + entry);
            }
        }

        return received;
    }

    private static String validHomeName(Object raw) {
        String name = Home.normalize(String.valueOf(raw));

        if (!Home.isValidName(name)) {
            throw new IllegalArgumentException("Invalid home name " + raw);
        }

        return name;
    }

    private double coordinate(ConfigurationSection entry, String field, String name, UUID owner) {
        Object value = entry.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalStateException("Home '" + name + "' for " + owner
                    + " has an invalid " + field + " coordinate");
        }
        return number.doubleValue();
    }

    /** Falls back to a leftover {@code .tmp}, which is what a crash mid-rename looks like. */
    private YamlConfiguration readOrRecover(UUID owner) {
        File file = fileFor(owner);
        File temp = tempFor(owner);

        YamlConfiguration parsed = tryParse(file);
        if (parsed != null) {
            return parsed;
        }

        if (file.isFile()) {
            logger.severe("Home file for " + owner + " is unreadable; trying recovery.");
        }

        parsed = tryParse(temp);
        if (parsed != null) {
            try {
                if (file.isFile()) {
                    Path quarantine = file.toPath().resolveSibling(file.getName() + ".corrupt-" + UUID.randomUUID());
                    Files.move(file.toPath(), quarantine);
                    logger.severe("Preserved unreadable homes for " + owner
                            + " as " + quarantine.getFileName());
                }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Could not recover home file for " + owner, e);
            }
            logger.warning("Recovered homes for " + owner + " from " + temp.getName());
            return parsed;
        }

        if (file.isFile() || temp.isFile()) {
            throw new IllegalStateException("Home file for " + owner
                    + " is unreadable; original files were left untouched");
        }
        return null;
    }

    private YamlConfiguration tryParse(File file) {
        if (!file.isFile()) {
            return null;
        }

        YamlConfiguration yaml = new YamlConfiguration();

        try {
            yaml.load(file);
            return yaml;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not parse " + file.getName(), e);
            return null;
        }
    }

    @Override
    public void save(PlayerHomes.Snapshot snapshot) {
        if (closing) {
            logger.warning("Dropping save for " + snapshot.owner() + ": storage is closing.");
            return;
        }

        // A write already queued for this owner will pick up the newest snapshot.
        if (pendingSaves.put(snapshot.owner(), snapshot) != null) {
            return;
        }

        try {
            executor.execute(() -> {
                PlayerHomes.Snapshot latest = pendingSaves.remove(snapshot.owner());

                if (latest == null) {
                    return;
                }

                try {
                    write(latest);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to save homes for " + latest.owner(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            pendingSaves.remove(snapshot.owner());
            logger.log(Level.SEVERE, "Storage rejected save for " + snapshot.owner(), e);
        }
    }

    /** Temp file plus atomic rename, so a crash mid-write never leaves a truncated file. */
    private void write(PlayerHomes.Snapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        yaml.set("last-known-name", snapshot.lastKnownName());
        yaml.set("last-teleport", snapshot.lastTeleport());

        for (Home home : snapshot.homes()) {
            String path = "homes." + home.name() + ".";
            yaml.set(path + "world", home.world());
            yaml.set(path + "x", home.x());
            yaml.set(path + "y", home.y());
            yaml.set(path + "z", home.z());
            yaml.set(path + "yaw", home.yaw());
            yaml.set(path + "pitch", home.pitch());
            yaml.set(path + "created", home.created());

            // Omitted when empty so players who share nothing keep their existing file.
            if (!home.sharedWith().isEmpty()) {
                yaml.set(path + "shared-with", home.sharedWith().stream().map(UUID::toString).toList());
            }
        }

        if (!snapshot.received().isEmpty()) {
            List<Map<String, String>> received = new ArrayList<>();

            for (PlayerHomes.Received hint : snapshot.received()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("owner", hint.owner().toString());
                entry.put("owner-name", hint.ownerName());
                entry.put("home", hint.home());
                received.add(entry);
            }

            yaml.set("received", received);
        }

        Path target = fileFor(snapshot.owner()).toPath();
        Path temp = tempFor(snapshot.owner()).toPath();

        Files.createDirectories(target.getParent());
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);

        try {
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void shutdown() {
        closing = true;
        executor.shutdown();

        try {
            // Blocking is intended: onDisable must not return before player data hits disk.
            if (!executor.awaitTermination(shutdownTimeoutSeconds.getAsInt(), TimeUnit.SECONDS)) {
                logger.severe("Timed out flushing home data - some writes may be lost.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private File fileFor(UUID owner) {
        return new File(directory, owner + ".yml");
    }

    private File tempFor(UUID owner) {
        return new File(directory, owner + ".yml.tmp");
    }
}

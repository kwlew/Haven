package dev.kwlew.haven.home;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.kernel.Inject;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Stores each player's homes in {@code plugins/Haven/homes/<uuid>.yml}, one file per player so a
 * single save never rewrites everyone's data.
 * <p>
 * Reads and writes run on a private single-threaded executor rather than the Bukkit scheduler. Bukkit
 * cancels queued plugin tasks at disable and forbids scheduling from {@code onDisable}, so a save
 * queued moments before shutdown would be dropped silently - unacceptable for a plugin whose only
 * job is persisting locations. FIFO ordering also makes a fast reconnect observe the quit save.
 */
public class YamlHomeStorage implements HomeStorage {

    private static final int SCHEMA_VERSION = 1;

    private final Logger logger;
    private final IntSupplier shutdownTimeoutSeconds;
    private final File directory;
    private final ExecutorService executor;

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
            // Non-daemon on purpose: pending writes must finish even if the JVM is winding down.
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
        // Reads share the save queue. A player who reconnects immediately after quitting must
        // observe the save queued by PlayerQuitEvent, not the previous version on disk.
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
        if (schema != SCHEMA_VERSION) {
            throw new IllegalStateException("Home file for " + owner + " uses schema v" + schema
                    + "; this build supports only v" + SCHEMA_VERSION);
        }

        // Prefer the name the caller supplied - it's current; the stored one may be a rename ago.
        String name = lastKnownName != null ? lastKnownName : yaml.getString("last-known-name");

        return new PlayerHomes.Snapshot(
                owner,
                name,
                yaml.getLong("last-teleport", 0L),
                readHomes(owner, yaml)
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
                    entry.getLong("created")
            ));
        }

        return homes;
    }

    private double coordinate(ConfigurationSection entry, String field, String name, UUID owner) {
        Object value = entry.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalStateException("Home '" + name + "' for " + owner
                    + " has an invalid " + field + " coordinate");
        }
        return number.doubleValue();
    }

    /**
     * Reads a player's file, falling back to a leftover {@code .tmp} if the main file is missing or
     * unparseable - which is what a crash mid-rename looks like.
     */
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

        try {
            executor.execute(() -> {
                try {
                    write(snapshot);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to save homes for " + snapshot.owner(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.log(Level.SEVERE, "Storage rejected save for " + snapshot.owner(), e);
        }
    }

    /**
     * Serialises to a temp file then atomically renames over the target, so a crash mid-write can
     * never leave a truncated file where a player's homes used to be.
     */
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
            // Blocking the main thread here is correct: onDisable is exactly when we must not
            // return before player data has hit disk.
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

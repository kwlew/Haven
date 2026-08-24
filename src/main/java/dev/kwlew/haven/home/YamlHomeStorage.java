package dev.kwlew.haven.home;

import dev.kwlew.haven.config.HavenConfig;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Stores each player's homes in {@code plugins/Haven/homes/<uuid>.yml}, one file per player so a
 * single save never rewrites everyone's data.
 * <p>
 * Writes run on a private single-threaded executor rather than the Bukkit scheduler. Bukkit
 * cancels queued plugin tasks at disable and forbids scheduling from {@code onDisable}, so a save
 * queued moments before shutdown would be dropped silently - unacceptable for a plugin whose only
 * job is persisting locations. A single thread also gives FIFO ordering, which removes any chance
 * of two writes for the same player interleaving.
 */
public class YamlHomeStorage implements HomeStorage {

    private static final int SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final File directory;
    private final ExecutorService executor;

    private volatile boolean closing;

    public YamlHomeStorage(JavaPlugin plugin, HavenConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.directory = new File(plugin.getDataFolder(), "homes");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
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
        YamlConfiguration yaml = readOrRecover(owner);

        if (yaml == null) {
            return new PlayerHomes.Snapshot(owner, lastKnownName, 0L, List.of());
        }

        int schema = yaml.getInt("schema-version", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            plugin.getLogger().warning("Home file for " + owner + " uses schema v" + schema
                    + " but this build understands v" + SCHEMA_VERSION
                    + ". Reading anyway; unknown fields will be dropped on the next save.");
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
            return homes;
        }

        for (String key : homesSection.getKeys(false)) {
            ConfigurationSection entry = homesSection.getConfigurationSection(key);
            String name = Home.normalize(key);

            if (entry == null) {
                continue;
            }

            String world = entry.getString("world");

            // Skip rather than fail: one hand-edited bad entry must not cost a player every home.
            if (!Home.isValidName(name)) {
                plugin.getLogger().warning("Skipping home with invalid name '" + key + "' for " + owner);
                continue;
            }

            if (world == null || world.isBlank()) {
                plugin.getLogger().warning("Skipping home '" + name + "' for " + owner + ": no world set");
                continue;
            }

            homes.add(new Home(
                    name,
                    world,
                    entry.getDouble("x"),
                    entry.getDouble("y"),
                    entry.getDouble("z"),
                    (float) entry.getDouble("yaw"),
                    (float) entry.getDouble("pitch"),
                    entry.getLong("created")
            ));
        }

        return homes;
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
            plugin.getLogger().severe("Home file for " + owner + " is unreadable; trying recovery.");
        }

        parsed = tryParse(temp);
        if (parsed != null) {
            plugin.getLogger().warning("Recovered homes for " + owner + " from " + temp.getName());
        }

        return parsed;
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
            plugin.getLogger().log(Level.WARNING, "Could not parse " + file.getName(), e);
            return null;
        }
    }

    @Override
    public void save(PlayerHomes.Snapshot snapshot) {
        if (closing) {
            plugin.getLogger().warning("Dropping save for " + snapshot.owner() + ": storage is closing.");
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    write(snapshot);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save homes for " + snapshot.owner(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "Storage rejected save for " + snapshot.owner(), e);
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
            if (!executor.awaitTermination(config.shutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out flushing home data - some writes may be lost.");
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

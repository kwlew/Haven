package dev.kwlew.haven.managers.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Typed access to {@code config.yml}, loaded eagerly in the constructor and re-read on {@link #reload()}. */
public class HavenConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Derived on reload so hot paths never re-parse YAML.
    private boolean cancelOnMove;
    private boolean cancelOnDamage;
    private boolean blockTeleportToDisabledWorlds;
    private boolean guiEnabled;
    private Set<String> disabledWorlds = Set.of();
    private Map<String, Integer> worldLimits = Map.of();

    public HavenConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration loaded = plugin.getConfig();

        try (InputStream bundled = plugin.getResource("config.yml")) {
            if (bundled == null) {
                plugin.getLogger().severe("Bundled config.yml is missing from the jar; "
                        + "falling back to built-in values for anything absent on disk.");
            } else {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled config.yml", e);
        }

        this.config = loaded;

        this.cancelOnMove = boolAt("teleport.cancel-on-move");
        this.cancelOnDamage = boolAt("teleport.cancel-on-damage");
        this.blockTeleportToDisabledWorlds = defined("worlds.block-teleport")
                && loaded.getBoolean("worlds.block-teleport");
        this.guiEnabled = defined("gui.enabled") && loaded.getBoolean("gui.enabled");
        this.disabledWorlds = defined("worlds.disabled")
                ? Set.copyOf(loaded.getStringList("worlds.disabled")) : Set.of();
        this.worldLimits = parseWorldLimits(loaded);
    }

    public int defaultHomeLimit() {
        return Math.max(0, intAt("homes.default-limit", 3));
    }

    public int confirmTimeoutSeconds() {
        return Math.max(1, intAt("homes.confirm-timeout-seconds", 15));
    }

    public int warmupSeconds() {
        return Math.max(0, intAt("teleport.warmup-seconds", 3));
    }

    public int cooldownSeconds() {
        return Math.max(0, intAt("teleport.cooldown-seconds", 10));
    }

    public boolean cancelOnMove() {
        return cancelOnMove;
    }

    public boolean cancelOnDamage() {
        return cancelOnDamage;
    }

    public boolean safeTeleportEnabled() {
        return boolAt("teleport.safe-teleport.enabled");
    }

    public int safeTeleportRadius() {
        return Math.max(0, Math.min(8, intAt("teleport.safe-teleport.search-radius", 3)));
    }

    public int safeTeleportHeight() {
        return Math.max(0, Math.min(16, intAt("teleport.safe-teleport.search-height", 4)));
    }

    public Set<String> disabledWorlds() {
        return disabledWorlds;
    }

    public boolean blockTeleportToDisabledWorlds() {
        return blockTeleportToDisabledWorlds;
    }

    /** Entries that are not whole numbers are skipped rather than treated as 0, so a typo can't forbid homes. */
    public Map<String, Integer> worldLimits() {
        return worldLimits;
    }

    private static Map<String, Integer> parseWorldLimits(FileConfiguration loaded) {
        Map<String, Integer> limits = new HashMap<>();
        ConfigurationSection section = loaded.getConfigurationSection("worlds.limits");

        if (section == null) {
            return Map.of();
        }

        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            Object value = entry.getValue();

            if (value instanceof Number number) {
                limits.put(entry.getKey(), Math.max(0, number.intValue()));
            } else if (value instanceof String text) {
                try {
                    limits.put(entry.getKey(), Math.max(0, Integer.parseInt(text.trim())));
                } catch (NumberFormatException ignored) {
                    // Skipped, as above.
                }
            }
        }

        return Map.copyOf(limits);
    }

    public boolean sharingEnabled() {
        return boolAt("sharing.enabled");
    }

    public int sharingMaxPerHome() {
        return Math.max(1, Math.min(100, intAt("sharing.max-per-home", 10)));
    }

    public int shutdownTimeoutSeconds() {
        return Math.max(1, intAt("storage.shutdown-timeout-seconds", 10));
    }

    public boolean guiEnabled() {
        return guiEnabled;
    }

    public boolean updateCheckerEnabled() {
        return boolAt("update-checker.enabled");
    }

    public int updateCheckIntervalHours() {
        return Math.max(1, Math.min(168, intAt("update-checker.interval-hours", 6)));
    }

    public boolean updateNotifyAdmins() {
        return boolAt("update-checker.notify-admins");
    }

    public boolean soundsEnabled() {
        return boolAt("sounds.enabled");
    }

    public String soundKey(String id) {
        return stringAt("sounds." + id + ".key", "");
    }

    public double soundVolume(String id) {
        return doubleAt("sounds." + id + ".volume");
    }

    public double soundPitch(String id) {
        return doubleAt("sounds." + id + ".pitch");
    }

    public String soundSource(String id) {
        return stringAt("sounds." + id + ".source", "master");
    }

    /**
     * Unlike {@code isSet}, also covers the bundled defaults. Used instead of two-argument getters, which shadow the
     * defaults chain.
     */
    private boolean defined(String path) {
        if (config.isSet(path)) {
            return true;
        }

        Configuration defaults = config.getDefaults();

        return defaults != null && defaults.isSet(path);
    }

    private int intAt(String path, int fallback) {
        return defined(path) ? config.getInt(path) : fallback;
    }

    private boolean boolAt(String path) {
        return !defined(path) || config.getBoolean(path);
    }

    private double doubleAt(String path) {
        return defined(path) ? config.getDouble(path) : 1.0;
    }

    private String stringAt(String path, String fallback) {
        String value = defined(path) ? config.getString(path) : null;

        return value != null ? value : fallback;
    }
}

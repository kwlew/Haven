package dev.kwlew.haven.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Typed access to {@code config.yml}.
 * <p>
 * Loads eagerly in the constructor: every component is constructed before any {@code init()} runs,
 * so deferring the load would let a dependent read unset values. For the same reason nothing here
 * is cached by callers - each getter reads the live configuration, which is what makes
 * {@link #reload()} take effect without a restart.
 * <p>
 * The jar's copy is installed as the defaults so a server upgrading from an older version picks up
 * keys its on-disk file predates, without rewriting (and de-commenting) that file. Reads go through
 * {@link #defined} rather than the two-argument Bukkit getters, because passing an explicit default
 * to {@code getInt(path, def)} <em>shadows</em> the defaults chain - which silently disabled every
 * new key on existing installs.
 */
public class HavenConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

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
        return boolAt("teleport.cancel-on-move", true);
    }

    public boolean cancelOnDamage() {
        return boolAt("teleport.cancel-on-damage", true);
    }

    public int shutdownTimeoutSeconds() {
        return Math.max(1, intAt("storage.shutdown-timeout-seconds", 10));
    }

    public boolean soundsEnabled() {
        return boolAt("sounds.enabled", true);
    }

    /** Blank means "this sound is switched off". */
    public String soundKey(String id) {
        return stringAt("sounds." + id + ".key", "");
    }

    public double soundVolume(String id) {
        return doubleAt("sounds." + id + ".volume", 1.0D);
    }

    public double soundPitch(String id) {
        return doubleAt("sounds." + id + ".pitch", 1.0D);
    }

    public String soundSource(String id) {
        return stringAt("sounds." + id + ".source", "master");
    }

    /**
     * True when the path exists in the player's file or in the bundled defaults. {@code isSet}
     * alone only covers the former.
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

    private boolean boolAt(String path, boolean fallback) {
        return defined(path) ? config.getBoolean(path) : fallback;
    }

    private double doubleAt(String path, double fallback) {
        return defined(path) ? config.getDouble(path) : fallback;
    }

    private String stringAt(String path, String fallback) {
        String value = defined(path) ? config.getString(path) : null;

        return value != null ? value : fallback;
    }
}

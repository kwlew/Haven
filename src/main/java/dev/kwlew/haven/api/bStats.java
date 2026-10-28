package dev.kwlew.haven.api;

import dev.kwlew.haven.globals.BuildINFO;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.managers.config.HavenConfig;
import dev.kwlew.haven.managers.home.HomeManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * bStats metrics. Chart callbacks run on bStats' thread, so they read only {@code volatile} snapshots refreshed by a
 * main-thread task.
 */
public class bStats implements LifecycleComponent {

    private static final long SNAPSHOT_INTERVAL_TICKS = 20L * 60L * 5L;

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final HomeManager homeManager;

    private Metrics metrics;
    private BukkitTask snapshotTask;

    private volatile boolean warmupEnabled;
    private volatile boolean cooldownEnabled;
    private volatile boolean soundsEnabled;
    private volatile String defaultLimit = "unknown";
    private volatile int homesOfOnlinePlayers;

    public bStats(JavaPlugin plugin, HavenConfig config, HomeManager homeManager) {
        this.plugin = plugin;
        this.config = config;
        this.homeManager = homeManager;
    }

    @Override
    public void start() {
        snapshot();

        metrics = new Metrics(plugin, BuildINFO.bStats_ID);

        metrics.addCustomChart(new SimplePie("warmup_enabled", () -> yesNo(warmupEnabled)));
        metrics.addCustomChart(new SimplePie("cooldown_enabled", () -> yesNo(cooldownEnabled)));
        metrics.addCustomChart(new SimplePie("sounds_enabled", () -> yesNo(soundsEnabled)));
        metrics.addCustomChart(new SimplePie("default_home_limit", () -> defaultLimit));
        metrics.addCustomChart(new SingleLineChart("homes_of_online_players", () -> homesOfOnlinePlayers));

        snapshotTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::snapshot, SNAPSHOT_INTERVAL_TICKS, SNAPSHOT_INTERVAL_TICKS);

        plugin.getLogger().info("bStats metrics enabled.");
    }

    @Override
    public void shutdown() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }

        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    private void snapshot() {
        warmupEnabled = config.warmupSeconds() > 0;
        cooldownEnabled = config.cooldownSeconds() > 0;
        soundsEnabled = config.soundsEnabled();
        defaultLimit = Integer.toString(config.defaultHomeLimit());
        homesOfOnlinePlayers = homeManager.cachedHomeTotal();
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}

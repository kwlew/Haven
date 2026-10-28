package dev.kwlew.haven.managers.config;

import dev.kwlew.haven.kernel.Inject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Upgrades older {@code config.yml} / {@code messages.yml} on construction; must be resolved before
 * {@link HavenConfig} and Messages, which read eagerly. The outgoing defaults are kept as
 * {@code migrations/<name>-<version>.yml} for {@link YamlMerge}: <strong>add one when bumping the version.</strong>
 * Never blocks startup or damages the original (backup, temp file, read-back); failures go to {@link #failedFiles()}.
 */
public class FileMigrator {

    static final List<String> FILES = List.of("config.yml", "messages.yml");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path folder;
    private final Logger logger;
    private final String pluginVersion;
    private final Function<String, InputStream> resources;
    private final Clock clock;
    private final List<String> failed = new ArrayList<>();

    @Inject
    public FileMigrator(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath(), plugin.getLogger(), plugin.getDescription().getVersion(),
                plugin::getResource, Clock.systemDefaultZone());
    }

    FileMigrator(Path folder, Logger logger, String pluginVersion,
                 Function<String, InputStream> resources, Clock clock) {
        this.folder = folder;
        this.logger = logger;
        this.pluginVersion = pluginVersion;
        this.resources = resources;
        this.clock = clock;

        FILES.forEach(this::migrate);
    }

    public List<String> failedFiles() {
        return List.copyOf(failed);
    }

    private void migrate(String name) {
        Path target = folder.resolve(name);
        Path temp = folder.resolve(name + ".tmp");

        if (!Files.isRegularFile(target)) {
            // Nothing to upgrade; the first load installs the bundled default.
            return;
        }

        try {
            String original = Files.readString(target, StandardCharsets.UTF_8);

            YamlConfiguration admin = new YamlConfiguration();
            admin.loadFromString(original);

            String bundled = readResource(name);
            int current = versionOf(bundled);
            int version = Math.max(1, admin.getInt(YamlMerge.VERSION_KEY, 1));

            if (version == current) {
                return;
            }

            if (version > current) {
                logger.warning(name + " is version " + version + ", newer than the version " + current
                        + " this Haven understands. Leaving it as it is.");
                return;
            }

            YamlConfiguration old = new YamlConfiguration();
            old.loadFromString(readResource("migrations/" + base(name) + "-" + version + ".yml"));

            YamlMerge.Result merged = YamlMerge.merge(bundled, old, admin);

            Files.writeString(temp, merged.text(), StandardCharsets.UTF_8);
            verify(temp, merged.applied(), current);

            Path backup = backup(target, name, version);
            move(temp, target);

            for (String path : merged.dropped()) {
                logger.warning(name + ": dropped unknown option " + path + " (kept in the backup)");
            }

            logger.info("Updated " + name + " to the " + pluginVersion + " format. "
                    + "Your old file is in backups/" + backup.getFileName() + ".");
        } catch (Exception e) {
            deleteQuietly(temp);
            failed.add(name);

            logger.warning("Couldn't update " + name + " automatically. Delete it (or compare with the "
                    + "default in the jar) and restart to get the new options.");
            logger.warning("Reason: " + e);
        }
    }

    /** A merge bug must surface here, while the original is still in place. */
    private void verify(Path written, Map<String, Object> applied, int version) throws Exception {
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(Files.readString(written, StandardCharsets.UTF_8));

        if (reread.getInt(YamlMerge.VERSION_KEY, -1) != version) {
            throw new IllegalStateException("the written file lost its " + YamlMerge.VERSION_KEY);
        }

        for (Map.Entry<String, Object> entry : applied.entrySet()) {
            if (!Objects.equals(YamlMerge.normalize(reread.get(entry.getKey())), entry.getValue())) {
                throw new IllegalStateException("'" + entry.getKey() + "' did not survive the rewrite");
            }
        }
    }

    private Path backup(Path target, String name, int version) throws IOException {
        Path directory = folder.resolve("backups");
        Files.createDirectories(directory);

        Path backup = directory.resolve(base(name) + "-v" + version + "-"
                + STAMP.format(LocalDateTime.now(clock)) + ".yml");
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);

        return backup;
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int versionOf(String bundled) throws Exception {
        YamlConfiguration parsed = new YamlConfiguration();
        parsed.loadFromString(bundled);

        int version = parsed.getInt(YamlMerge.VERSION_KEY, -1);

        if (version < 1) {
            throw new IllegalStateException("the bundled file has no " + YamlMerge.VERSION_KEY);
        }

        return version;
    }

    private String readResource(String path) throws IOException {
        try (InputStream stream = resources.apply(path)) {
            if (stream == null) {
                throw new IllegalStateException(path + " is missing from the jar");
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String base(String name) {
        return name.substring(0, name.length() - ".yml".length());
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: a stray .tmp is overwritten by the next attempt.
        }
    }
}

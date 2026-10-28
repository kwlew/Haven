package dev.kwlew.haven.managers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileMigratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T10:11:12Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    @Test
    void upgradesBothFilesAndKeepsTheOldOnesInBackups() throws IOException {
        Files.writeString(folder.resolve("config.yml"),
                resource("/migrations/config-1.yml").replace("default-limit: 3", "default-limit: 9"));
        Files.writeString(folder.resolve("messages.yml"), resource("/migrations/messages-1.yml"));

        FileMigrator migrator = migrate();

        assertEquals(List.of(), migrator.failedFiles());
        assertEquals(resource("/config.yml").replace("default-limit: 3", "default-limit: 9"),
                Files.readString(folder.resolve("config.yml")));
        assertEquals(resource("/messages.yml"), Files.readString(folder.resolve("messages.yml")));
        assertTrue(Files.readString(folder.resolve("backups/config-v1-20261025-101112.yml")).contains("default-limit: 9"));
        assertEquals(resource("/migrations/messages-1.yml"),
                Files.readString(folder.resolve("backups/messages-v1-20261025-101112.yml")));
        assertFalse(Files.exists(folder.resolve("config.yml.tmp")));
    }

    @Test
    void secondRunChangesNothing() throws IOException {
        Files.writeString(folder.resolve("config.yml"), resource("/migrations/config-1.yml"));
        migrate();

        long backups = count(folder.resolve("backups"));
        String after = Files.readString(folder.resolve("config.yml"));

        migrate();

        assertEquals(after, Files.readString(folder.resolve("config.yml")));
        assertEquals(backups, count(folder.resolve("backups")));
    }

    @Test
    void missingFilesAreLeftForTheNormalFirstRunInstall() {
        FileMigrator migrator = migrate();

        assertEquals(List.of(), migrator.failedFiles());
        assertFalse(Files.exists(folder.resolve("config.yml")));
        assertFalse(Files.exists(folder.resolve("backups")));
    }

    @Test
    void aFileFromANewerHavenIsLeftAlone() throws IOException {
        String newer = "config-version: 99\nhomes:\n  default-limit: 1\n";
        Files.writeString(folder.resolve("config.yml"), newer);

        FileMigrator migrator = migrate();

        assertEquals(List.of(), migrator.failedFiles());
        assertEquals(newer, Files.readString(folder.resolve("config.yml")));
        assertFalse(Files.exists(folder.resolve("backups")));
    }

    @Test
    void anUnreadableFileIsLeftUntouchedAndReported() throws IOException {
        String broken = "homes: [unclosed\n  default-limit: : 3\n";
        Files.writeString(folder.resolve("config.yml"), broken);
        Files.writeString(folder.resolve("messages.yml"), resource("/migrations/messages-1.yml"));

        FileMigrator migrator = migrate();

        assertEquals(List.of("config.yml"), migrator.failedFiles());
        assertEquals(broken, Files.readString(folder.resolve("config.yml")));
        assertFalse(Files.exists(folder.resolve("config.yml.tmp")));
        assertFalse(Files.exists(folder.resolve("backups/config-v1-20261025-101112.yml")));
        // One file failing must not stop the other from upgrading.
        assertEquals(resource("/messages.yml"), Files.readString(folder.resolve("messages.yml")));
    }

    @Test
    void aMissingSnapshotFailsSafelyInsteadOfGuessing() throws IOException {
        Files.writeString(folder.resolve("config.yml"), "config-version: 1\n");
        Files.writeString(folder.resolve("messages.yml"), "x: y\n");

        FileMigrator migrator = new FileMigrator(folder, Logger.getAnonymousLogger(), "1.2.0",
                path -> path.startsWith("migrations/") ? null : FileMigratorTest.class.getResourceAsStream("/" + path),
                CLOCK);

        assertEquals(List.of("config.yml", "messages.yml"), migrator.failedFiles());
        assertEquals("x: y\n", Files.readString(folder.resolve("messages.yml")));
    }

    private FileMigrator migrate() {
        return new FileMigrator(folder, Logger.getAnonymousLogger(), "1.2.0",
                path -> FileMigratorTest.class.getResourceAsStream("/" + path), CLOCK);
    }

    private static long count(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        }
    }

    private static String resource(String path) {
        try (InputStream stream = FileMigratorTest.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}

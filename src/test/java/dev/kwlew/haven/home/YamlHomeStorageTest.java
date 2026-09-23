package dev.kwlew.haven.home;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlHomeStorageTest {

    @TempDir Path dataFolder;

    private YamlHomeStorage storage;
    private ExecutorService io;

    @BeforeEach
    void createStorage() {
        io = Executors.newSingleThreadExecutor();
        storage = new YamlHomeStorage(dataFolder.resolve("homes"),
                Logger.getLogger("YamlHomeStorageTest"), () -> 5, io);
        storage.init();
    }

    @AfterEach
    void closeStorage() {
        storage.shutdown();
    }

    @Test
    void reconnectReadWaitsForQueuedSave() throws Exception {
        UUID owner = UUID.randomUUID();
        Home home = new Home("base", "world", 1, 64, 2, 0, 0, 123);
        CountDownLatch releaseIo = new CountDownLatch(1);
        io.execute(() -> {
            try {
                releaseIo.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        storage.save(new PlayerHomes.Snapshot(owner, "Alice", 0, List.of(home)));

        ExecutorService loginThread = Executors.newSingleThreadExecutor();
        try {
            Future<PlayerHomes.Snapshot> read = loginThread.submit(() -> storage.load(owner, "Alice"));
            assertThrows(TimeoutException.class, () -> read.get(100, TimeUnit.MILLISECONDS));
            releaseIo.countDown();
            assertEquals(List.of(home), read.get(5, TimeUnit.SECONDS).homes());
        } finally {
            releaseIo.countDown();
            loginThread.shutdownNow();
        }
    }

    @Test
    void corruptFileIsNeverReplacedWithAnEmptySnapshot() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        String damaged = "homes: [unterminated";
        Files.writeString(file, damaged);

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals(damaged, Files.readString(file));
    }

    @Test
    void futureSchemaIsNeverRewritten() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        String future = "schema-version: 99\nhomes: {}\n";
        Files.writeString(file, future);

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals(future, Files.readString(file));
    }

    @Test
    void emptyFileIsNotAcceptedAsAnEmptyAccount() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        Files.writeString(file, "");

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals("", Files.readString(file));
    }

    @Test
    void malformedHomesSectionIsPreserved() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        String malformed = "schema-version: 1\nhomes: [base]\n";
        Files.writeString(file, malformed);

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals(malformed, Files.readString(file));
    }

    @Test
    void validTempRecoversAndQuarantinesDamagedMainFile() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        Files.writeString(file, "homes: [unterminated");
        Files.writeString(file.resolveSibling(file.getFileName() + ".tmp"),
                "schema-version: 1\nhomes:\n  base:\n    world: world\n"
                        + "    x: 1\n    y: 64\n    z: 2\n    yaw: 0\n    pitch: 0\n    created: 123\n");

        assertEquals("base", storage.load(owner, "Alice").homes().get(0).name());
        assertTrue(Files.readString(file).contains("base:"));
        try (var files = Files.list(file.getParent())) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith(owner + ".yml.corrupt-")));
        }
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
    }
}

package dev.kwlew.haven.managers.home;

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
    void rapidSavesForOneOwnerLeaveTheLatestDataOnDisk() throws Exception {
        UUID owner = UUID.randomUUID();
        Home first = new Home("first", "world", 1, 64, 2, 0, 0, 1);
        Home second = new Home("second", "world", 3, 64, 4, 0, 0, 2);
        CountDownLatch releaseIo = new CountDownLatch(1);
        io.execute(() -> {
            try {
                releaseIo.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        storage.save(new PlayerHomes.Snapshot(owner, "Alice", 0, List.of(first)));
        storage.save(new PlayerHomes.Snapshot(owner, "Alice", 0, List.of(second)));
        releaseIo.countDown();

        assertEquals(List.of(second), storage.load(owner, "Alice").homes());
        assertFalse(Files.readString(dataFolder.resolve("homes").resolve(owner + ".yml")).contains("first"));
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

    @Test
    void asyncLoadOfMissingFileIsEmptyAndDoesNotCreateOne() throws Exception {
        UUID owner = UUID.randomUUID();

        PlayerHomes.Snapshot snapshot = storage.loadAsync(owner, null).get(5, TimeUnit.SECONDS);

        assertEquals(List.of(), snapshot.homes());
        assertFalse(Files.exists(dataFolder.resolve("homes").resolve(owner + ".yml")));
    }

    @Test
    void asyncLoadWaitsForQueuedSaveWithoutBlockingTheCaller() throws Exception {
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

        var read = storage.loadAsync(owner, null);

        assertFalse(read.isDone());
        releaseIo.countDown();
        assertEquals(List.of(home), read.get(5, TimeUnit.SECONDS).homes());
    }

    @Test
    void asyncLoadOfDamagedFileFailsTheFuture() throws Exception {
        UUID owner = UUID.randomUUID();
        Files.writeString(dataFolder.resolve("homes").resolve(owner + ".yml"), "homes: [unterminated");

        var read = storage.loadAsync(owner, null);

        assertThrows(java.util.concurrent.ExecutionException.class, () -> read.get(5, TimeUnit.SECONDS));
    }

    @Test
    void v1FileLoadsWithoutSharesAndIsWrittenBackAsV2() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        Files.writeString(file, "schema-version: 1\nlast-known-name: Alice\nlast-teleport: 7\nhomes:\n"
                + "  base:\n    world: world\n    x: 1\n    y: 64\n    z: 2\n    yaw: 0\n"
                + "    pitch: 0\n    created: 123\n");

        PlayerHomes.Snapshot loaded = storage.load(owner, "Alice");

        assertEquals(List.of(new Home("base", "world", 1, 64, 2, 0, 0, 123)), loaded.homes());
        assertTrue(loaded.homes().get(0).sharedWith().isEmpty());
        assertEquals(List.of(), loaded.received());

        storage.save(loaded);
        // Reads share the save queue, so this one sees the write.
        assertEquals(loaded, storage.load(owner, "Alice"));
        assertTrue(Files.readString(file).contains("schema-version: 2"));
    }

    @Test
    void v2RoundTripKeepsSharesAndReceivedHints() {
        UUID owner = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        Home shared = new Home("base", "world", 1, 64, 2, 0, 0, 123).withSharedWith(bob).withSharedWith(carol);
        Home plain = new Home("farm", "world_nether", 3, 70, 4, 90f, 10f, 456);
        PlayerHomes.Snapshot saved = new PlayerHomes.Snapshot(owner, "Alice", 9, List.of(shared, plain),
                List.of(new PlayerHomes.Received(friend, "Dave", "castle"),
                        new PlayerHomes.Received(bob, "Bob", "cabin")));

        storage.save(saved);
        PlayerHomes.Snapshot loaded = storage.load(owner, "Alice");

        assertEquals(saved, loaded);
        assertEquals(List.of(bob, carol), List.copyOf(loaded.homes().get(0).sharedWith()));
        assertTrue(loaded.homes().get(1).sharedWith().isEmpty());
    }

    @Test
    void unsharedHomesAndNoHintsLeaveTheFileFreeOfShareKeys() throws Exception {
        UUID owner = UUID.randomUUID();

        storage.save(new PlayerHomes.Snapshot(owner, "Alice", 0,
                List.of(new Home("base", "world", 1, 64, 2, 0, 0, 123))));
        storage.load(owner, "Alice");

        String written = Files.readString(dataFolder.resolve("homes").resolve(owner + ".yml"));
        assertFalse(written.contains("shared-with"));
        assertFalse(written.contains("received"));
    }

    @Test
    void unknownFutureSchemaIsRefusedAndLeftUntouched() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        String future = "schema-version: 3\nhomes: {}\n";
        Files.writeString(file, future);

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals(future, Files.readString(file));
    }

    @Test
    void invalidSharedWithIdRefusesTheFileRatherThanDroppingTheShare() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = dataFolder.resolve("homes").resolve(owner + ".yml");
        String damaged = "schema-version: 2\nhomes:\n  base:\n    world: world\n    x: 1\n    y: 64\n"
                + "    z: 2\n    yaw: 0\n    pitch: 0\n    created: 1\n    shared-with:\n    - not-a-uuid\n";
        Files.writeString(file, damaged);

        assertThrows(IllegalStateException.class, () -> storage.load(owner, "Alice"));
        assertEquals(damaged, Files.readString(file));
    }

    @Test
    void malformedReceivedEntriesAreSkippedAndTheRestKept() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        Files.writeString(dataFolder.resolve("homes").resolve(owner + ".yml"),
                "schema-version: 2\nreceived:\n"
                        + "- owner: not-a-uuid\n  owner-name: Bad\n  home: castle\n"
                        + "- owner: " + friend + "\n  owner-name: Dave\n  home: Bad Name\n"
                        + "- owner: " + friend + "\n  owner-name: Dave\n  home: castle\n");

        assertEquals(List.of(new PlayerHomes.Received(friend, "Dave", "castle")),
                storage.load(owner, "Alice").received());
    }
}

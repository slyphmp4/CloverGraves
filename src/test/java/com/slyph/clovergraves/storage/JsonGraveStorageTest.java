package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonGraveStorageTest {
    @TempDir
    Path tempDir;

    private GraveRecord record() {
        return new GraveRecord(-1, UUID.randomUUID(), "Steve", "world;0;64;0;0;0",
                new byte[]{1}, 1, 20, 1234, null, null);
    }

    @Test
    void corruptJsonIsNeverOverwrittenBySaveOrClose() throws Exception {
        Path file = tempDir.resolve("data.json");
        Files.writeString(file, "[broken");
        JsonGraveStorage storage = new JsonGraveStorage(tempDir.toFile());
        assertThrows(IllegalStateException.class, storage::loadAll);
        assertThrows(IllegalStateException.class, () -> storage.save(record()));
        storage.close();
        assertEquals("[broken", Files.readString(file));
    }

    @Test
    void failedWriteIsReportedAndRetryDoesNotDuplicateRecords() throws Exception {
        JsonGraveStorage storage = new JsonGraveStorage(tempDir.toFile());
        Path file = tempDir.resolve("data.json");
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "not a file");
        GraveRecord record = record();
        assertThrows(IllegalStateException.class, () -> storage.save(record));
        Files.delete(file.resolve("blocker"));
        Files.delete(file);
        assertTrue(storage.save(record) > 0);
        assertEquals(1, new JsonGraveStorage(tempDir.toFile()).loadAll().size());
    }

    @Test
    void repeatedLoadDoesNotDuplicateExistingRecords() {
        JsonGraveStorage storage = new JsonGraveStorage(tempDir.toFile());
        storage.save(record());
        assertEquals(1, storage.loadAll().size());
        assertEquals(1, storage.loadAll().size());
        storage.save(record());
        assertEquals(2, new JsonGraveStorage(tempDir.toFile()).loadAll().size());
    }

    @Test
    void bulkSavePersistsOneThousandGravesInOneDataset() {
        JsonGraveStorage storage = new JsonGraveStorage(tempDir.toFile());
        UUID owner = UUID.randomUUID();
        List<GraveRecord> records = new ArrayList<>();

        for (int i = 0; i < 1_000; i++) {
            records.add(new GraveRecord(
                    -1,
                    owner,
                    "Player-" + i,
                    "world;" + i + ";64.0;0.0;0.0;0.0",
                    new byte[]{(byte) i},
                    3465,
                    i,
                    1_000L + i,
                    null,
                    null
            ));
        }

        List<Long> ids = storage.saveAll(records);
        assertEquals(1_000, ids.size());
        assertTrue(ids.stream().allMatch(id -> id > 0));

        JsonGraveStorage reloaded = new JsonGraveStorage(tempDir.toFile());
        List<GraveRecord> restored = reloaded.loadAll();
        assertEquals(1_000, restored.size());
        assertTrue(restored.stream().allMatch(record -> record.owner().equals(owner)));
    }
}

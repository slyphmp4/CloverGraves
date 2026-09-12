package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonGraveStorageTest {
    @TempDir
    Path tempDir;

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

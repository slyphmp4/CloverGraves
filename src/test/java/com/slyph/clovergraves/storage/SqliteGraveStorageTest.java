package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteGraveStorageTest {
    @TempDir Path directory;

    @Test
    void fileBackendSupportsBatchArchiveAndRestoreClaimRollback() {
        SqlGraveStorage storage = new SqlGraveStorage(new JdbcConfig(JdbcConfig.Type.SQLITE,
                "jdbc:sqlite:" + directory.resolve("graves.db"), "", "", "axgraves_"), true, 2, 14);
        try {
            storage.init();
            UUID owner = UUID.randomUUID();
            GraveRecord record = new GraveRecord(-1, owner, "Steve", "world;0;64;0;0;0",
                    new byte[]{1, 2}, 1, 12, 1000, null, null);
            List<Long> ids = storage.saveAll(List.of(record, record));
            assertEquals(2, ids.size());
            assertNotEquals(ids.get(0), ids.get(1));
            storage.remove(ids.getFirst(), EndReason.EXPIRED);
            storage.remove(ids.getFirst(), EndReason.EXPIRED);
            assertEquals(1, storage.loadAll().size());
            assertEquals(1, storage.history(owner, 10).size());
            long historyId = storage.history(owner, 1).getFirst().id();
            assertTrue(storage.claimForRestore(historyId));
            storage.releaseRestoreClaim(historyId);
            assertTrue(storage.claimForRestore(historyId));
        } finally {
            storage.close();
        }
    }
}

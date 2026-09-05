package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGraveStorageTest {
    private String jdbcUrl;
    private SqlGraveStorage storage;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:clovergraves-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        JdbcConfig config = new JdbcConfig(JdbcConfig.Type.H2, jdbcUrl, "", "", "axgraves_");
        storage = new SqlGraveStorage(config, this::openConnection, true, 2, 30);
        storage.init();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private Connection openConnection() {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private GraveRecord newRecord(UUID owner) {
        return new GraveRecord(-1, owner, "Steve", "world;0.0;64.0;0.0;0.0;0.0",
                new byte[]{1, 2, 3}, 3465, 42, System.currentTimeMillis(), null, null);
    }

    @Test
    void savingANewRecordAssignsAnIdAndReloadsIt() {
        UUID owner = UUID.randomUUID();
        long id = storage.save(newRecord(owner));
        assertTrue(id > 0);
        List<GraveRecord> all = storage.loadAll();
        assertEquals(1, all.size());
        assertEquals(owner, all.getFirst().owner());
        assertEquals(42, all.getFirst().storedXP());
    }

    @Test
    void savingWithAnExistingIdUpdatesInPlace() {
        UUID owner = UUID.randomUUID();
        long id = storage.save(newRecord(owner));
        GraveRecord updated = new GraveRecord(id, owner, "Steve", "world;1.0;65.0;1.0;0.0;0.0",
                new byte[]{9}, 3465, 99, System.currentTimeMillis(), null, null);
        assertEquals(id, storage.save(updated));
        List<GraveRecord> all = storage.loadAll();
        assertEquals(1, all.size());
        assertEquals(99, all.getFirst().storedXP());
    }

    @Test
    void removingALiveGraveDeletesItAndArchivesToHistory() {
        UUID owner = UUID.randomUUID();
        long id = storage.save(newRecord(owner));
        storage.remove(id, EndReason.LOOTED);
        assertTrue(storage.loadAll().isEmpty());
        List<GraveRecord> history = storage.history(owner, 10);
        assertEquals(1, history.size());
        assertEquals(EndReason.LOOTED, history.getFirst().endReason());
        assertFalse(history.getFirst().restored());
    }

    @Test
    void claimForRestoreIsAtomicPerEntry() {
        UUID owner = UUID.randomUUID();
        long id = storage.save(newRecord(owner));
        storage.remove(id, EndReason.EXPIRED);
        long historyId = storage.history(owner, 1).getFirst().id();
        assertTrue(storage.claimForRestore(historyId));
        assertFalse(storage.claimForRestore(historyId));
        Optional<GraveRecord> entry = storage.historyEntry(historyId);
        assertTrue(entry.isPresent());
        assertTrue(entry.get().restored());
    }

    @Test
    void historyRetentionKeepsOnlyTheConfiguredCountPerPlayer() {
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            long id = storage.save(newRecord(owner));
            storage.remove(id, EndReason.EXPIRED);
        }
        assertEquals(2, storage.history(owner, 100).size());
    }

    @Test
    void historyIsIsolatedPerOwner() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        long idA = storage.save(newRecord(ownerA));
        storage.remove(idA, EndReason.EXPIRED);
        assertEquals(1, storage.history(ownerA, 10).size());
        assertTrue(storage.history(ownerB, 10).isEmpty());
    }
}

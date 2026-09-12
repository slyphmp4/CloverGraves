package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageMigrationTest {
    @TempDir
    File dataFolder;

    private String jdbcUrl;
    private SqlGraveStorage target;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:migration-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        JdbcConfig config = new JdbcConfig(JdbcConfig.Type.H2, jdbcUrl, "", "", "axgraves_");
        target = new SqlGraveStorage(config, this::openConnection, true, 5, 14);
        target.init();
    }

    @AfterEach
    void tearDown() {
        target.close();
    }

    private Connection openConnection() {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void writeLegacyDataJson(UUID owner) throws Exception {
        String json = """
                [
                  {
                    "owner": "%s",
                    "ownerName": "Steve",
                    "location": "world;0.0;64.0;0.0;0.0;0.0",
                    "items": "AAAAAA==",
                    "dataVersion": 3465,
                    "xp": 17,
                    "date": 1700000000000
                  }
                ]
                """.formatted(owner);
        Files.writeString(new File(dataFolder, "data.json").toPath(), json, StandardCharsets.UTF_8);
    }

    @Test
    void migratesLegacyRecordsIntoAnEmptyDatabase() throws Exception {
        UUID owner = UUID.randomUUID();
        writeLegacyDataJson(owner);
        StorageMigration.migrateIfNeeded(dataFolder, target);
        List<GraveRecord> all = target.loadAll();
        assertEquals(1, all.size());
        assertEquals(owner, all.getFirst().owner());
        assertEquals(17, all.getFirst().storedXP());
    }

    @Test
    void renamesTheSourceFileAfterASuccessfulMigration() throws Exception {
        writeLegacyDataJson(UUID.randomUUID());
        StorageMigration.migrateIfNeeded(dataFolder, target);
        assertFalse(new File(dataFolder, "data.json").exists());
        assertTrue(new File(dataFolder, "data.json.migrated").exists());
    }

    @Test
    void doesNothingWhenNoLegacyFileExists() {
        StorageMigration.migrateIfNeeded(dataFolder, target);
        assertTrue(target.loadAll().isEmpty());
    }

    @Test
    void doesNotOverwriteAnAlreadyPopulatedDatabase() throws Exception {
        UUID existingOwner = UUID.randomUUID();
        target.save(new GraveRecord(-1, existingOwner, "Alex", "world;0.0;64.0;0.0;0.0;0.0",
                new byte[]{1}, 3465, 5, System.currentTimeMillis(), null, null));
        writeLegacyDataJson(UUID.randomUUID());
        StorageMigration.migrateIfNeeded(dataFolder, target);
        List<GraveRecord> all = target.loadAll();
        assertEquals(1, all.size());
        assertEquals(existingOwner, all.getFirst().owner());
        assertTrue(new File(dataFolder, "data.json").exists());
    }

    @Test
    void failedBatchPreservesSourceAndRollsBackAlreadyInsertedRecords() throws Exception {
        writeLegacyDataJson(UUID.randomUUID());
        var file = new File(dataFolder, "data.json").toPath();
        var array = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        var invalid = array.get(0).getAsJsonObject().deepCopy();
        array.get(0).getAsJsonObject().addProperty("xp", 5);
        array.add(invalid);
        String original = array.toString();
        Files.writeString(file, original);
        try (Connection connection = openConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE axgraves_graves ADD CONSTRAINT reject_xp CHECK (stored_xp < 10)");
        }
        assertThrows(IllegalStateException.class, () -> StorageMigration.migrateIfNeeded(dataFolder, target));
        assertEquals(original, Files.readString(new File(dataFolder, "data.json").toPath()));
        assertTrue(target.loadAll().isEmpty());
        assertFalse(new File(dataFolder, "data.json.migrated").exists());
    }
}

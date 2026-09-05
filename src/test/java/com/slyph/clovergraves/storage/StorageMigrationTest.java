package com.slyph.clovergraves.storage;

import com.artillexstudios.axapi.database.DatabaseConfig;
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

class StorageMigrationTest {

    @TempDir
    File dataFolder;

    private String jdbcUrl;
    private SqlGraveStorage target;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:migration-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        DatabaseConfig config = new DatabaseConfig();
        config.tablePrefix("axgraves_");
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
                    "location": "world,0.0,64.0,0.0,0.0,0.0",
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
        assertEquals(owner, all.get(0).owner());
        assertEquals(17, all.get(0).storedXP());
    }

    @Test
    void renamesTheSourceFileAfterASuccessfulMigration() throws Exception {
        writeLegacyDataJson(UUID.randomUUID());

        StorageMigration.migrateIfNeeded(dataFolder, target);

        assertFalse(new File(dataFolder, "data.json").exists(), "data.json should be renamed away, not deleted or left in place");
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
        target.save(new GraveRecord(-1, existingOwner, "Alex", "world,0.0,64.0,0.0,0.0,0.0",
                new byte[]{1}, 3465, 5, System.currentTimeMillis(), null, null));

        writeLegacyDataJson(UUID.randomUUID());
        StorageMigration.migrateIfNeeded(dataFolder, target);

        List<GraveRecord> all = target.loadAll();
        assertEquals(1, all.size(), "the legacy file must not be imported on top of existing data");
        assertEquals(existingOwner, all.get(0).owner());
        assertTrue(new File(dataFolder, "data.json").exists(), "the untouched legacy file should be left in place for manual inspection");
    }
}

package com.slyph.clovergraves.storage;

import com.slyph.clovergraves.utils.CloverLogger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class StorageMigration {

    private StorageMigration() {
    }

    public static void migrateIfNeeded(@NotNull File dataFolder, @NotNull GraveStorage target) {
        File legacy = new File(dataFolder, "data.json");
        if (!legacy.exists()) return;

        if (!target.loadAll().isEmpty()) {
            CloverLogger.warn("found a legacy data.json next to an already-populated database - leaving it untouched. Delete it manually once you've confirmed the database has everything it should.");
            return;
        }

        JsonGraveStorage source = new JsonGraveStorage(dataFolder);
        List<GraveRecord> records = source.loadAll();
        if (records.isEmpty()) return;

        int migrated = 0;
        for (GraveRecord record : records) {
            try {
                target.save(record.withId(-1));
                migrated++;
            } catch (Exception ex) {
                CloverLogger.error("failed to migrate one grave from data.json - it will be skipped", ex);
            }
        }

        CloverLogger.info("migrated {}/{} grave(s) from data.json into the database", migrated, records.size());

        try {
            Path renamed = legacy.toPath().resolveSibling("data.json.migrated");
            Files.move(legacy.toPath(), renamed, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            CloverLogger.error("migration succeeded but renaming data.json failed - rename or delete it manually to avoid re-migrating on the next start", ex);
        }
    }
}

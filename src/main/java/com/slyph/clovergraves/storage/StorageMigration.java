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

        try {
            List<Long> ids = target.saveAll(records.stream().map(record -> record.withId(-1)).toList());
            if (ids.size() != records.size() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalStateException("storage did not confirm every migrated grave");
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("grave migration failed; data.json was left untouched", ex);
        }

        CloverLogger.info("migrated {} grave(s) from data.json into the database", records.size());

        try {
            Path renamed = legacy.toPath().resolveSibling("data.json.migrated");
            Files.move(legacy.toPath(), renamed, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            CloverLogger.error("migration succeeded but renaming data.json failed - rename or delete it manually to avoid re-migrating on the next start", ex);
        }
    }
}

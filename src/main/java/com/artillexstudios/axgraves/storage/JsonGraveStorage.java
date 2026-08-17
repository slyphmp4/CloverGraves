package com.artillexstudios.axgraves.storage;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Legacy/fallback storage: the full set of live graves as one {@code data.json} array, matching
 * the original file layout so it doubles as {@link StorageMigration}'s source.
 *
 * <p>Fixes relative to the original implementation:</p>
 * <ul>
 *   <li><b>Atomic writes.</b> Writes go to a sibling temp file first, then
 *       {@link Files#move(Path, Path, java.nio.file.CopyOption...)} with
 *       {@code ATOMIC_MOVE}, so a crash mid-write can never leave a truncated/corrupt
 *       {@code data.json} - the old file is either fully replaced or left untouched. The
 *       original used {@code new FileWriter(file)}, which truncates before a single byte of the
 *       new content is written.</li>
 *   <li><b>Safe restore.</b> The file is never deleted before load; each array entry is parsed
 *       in its own try/catch and a bad one is quarantined to {@code quarantine/} with a log line
 *       instead of aborting the shared loop and silently discarding every entry after it.</li>
 * </ul>
 *
 * <p>Does not support grave history/restore (feature C) - it only ever holds live grave state as
 * a whole-file snapshot, which doesn't fit an append-only history log without becoming a bespoke
 * database. This is the resilience fallback for when the SQL driver can't be fetched; use
 * {@code storage.type: H2} (the default) for history/restore.</p>
 */
public class JsonGraveStorage implements GraveStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File dataFile;
    private final File quarantineDir;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, GraveRecord> live = new ConcurrentHashMap<>();

    public JsonGraveStorage(@NotNull File dataFolder) {
        this.dataFile = new File(dataFolder, "data.json");
        this.quarantineDir = new File(dataFolder, "quarantine");
    }

    @Override
    public void init() {
        // nothing to set up - the file is created lazily on first save()
    }

    @Override
    @NotNull
    public List<GraveRecord> loadAll() {
        List<GraveRecord> result = new ArrayList<>();
        if (!dataFile.exists()) return result;

        JsonArray array;
        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            array = GSON.fromJson(json, JsonArray.class);
        } catch (Exception ex) {
            LogUtils.error("failed to read data.json - leaving it untouched, no graves were restored", ex);
            return result;
        }
        if (array == null) return result;

        int quarantined = 0;
        for (JsonElement el : array) {
            try {
                JsonObject obj = el.getAsJsonObject();
                long id = nextId.getAndIncrement();
                GraveRecord record = new GraveRecord(
                        id,
                        UUID.fromString(obj.get("owner").getAsString()),
                        obj.has("ownerName") && !obj.get("ownerName").isJsonNull() ? obj.get("ownerName").getAsString() : null,
                        obj.get("location").getAsString(),
                        Base64.getDecoder().decode(obj.get("items").getAsString()),
                        obj.has("dataVersion") ? obj.get("dataVersion").getAsInt() : -1,
                        obj.get("xp").getAsInt(),
                        obj.get("date").getAsLong(),
                        null,
                        null
                );
                result.add(record);
                live.put(id, record);
                if (id >= nextId.get()) nextId.set(id + 1);
            } catch (Exception ex) {
                quarantined++;
                quarantine(el, ex);
            }
        }

        if (quarantined > 0) {
            LogUtils.error("quarantined {} unreadable grave(s) from data.json into quarantine/ - see above for details", quarantined);
        }

        return result;
    }

    private void quarantine(@NotNull JsonElement entry, @NotNull Exception cause) {
        try {
            if (!quarantineDir.exists()) quarantineDir.mkdirs();
            File out = new File(quarantineDir, "grave-" + System.currentTimeMillis() + "-" + Math.abs(entry.hashCode()) + ".json");
            Files.writeString(out.toPath(), GSON.toJson(entry), StandardCharsets.UTF_8);
            LogUtils.error("could not load a grave entry, quarantined to {}", out.getName(), cause);
        } catch (IOException ex) {
            LogUtils.error("failed to quarantine an unreadable grave entry", ex);
        }
    }

    @Override
    public long save(@NotNull GraveRecord record) {
        GraveRecord toStore = record.id() > 0 ? record : record.withId(nextId.getAndIncrement());
        live.put(toStore.id(), toStore);
        flush();
        return toStore.id();
    }

    @Override
    public void remove(long id, @NotNull EndReason reason) {
        live.remove(id);
        flush();
    }

    private void flush() {
        JsonArray array = new JsonArray(live.size());
        for (GraveRecord record : live.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("owner", record.owner().toString());
            obj.addProperty("ownerName", record.ownerName());
            obj.addProperty("location", record.location());
            obj.addProperty("items", Base64.getEncoder().encodeToString(record.items()));
            obj.addProperty("dataVersion", record.dataVersion());
            obj.addProperty("xp", record.storedXP());
            obj.addProperty("date", record.createdAt());
            array.add(obj);
        }

        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            Path tmp = Files.createTempFile(dataFile.toPath().getParent(), "data", ".json.tmp");
            Files.writeString(tmp, GSON.toJson(array), StandardCharsets.UTF_8);
            Files.move(tmp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LogUtils.error("failed to save data.json", ex);
        }
    }

    @Override
    @NotNull
    public List<GraveRecord> history(@NotNull UUID owner, int limit) {
        return List.of();
    }

    @Override
    public boolean claimForRestore(long historyId) {
        LogUtils.warn("grave history/restore requires storage.type: H2 (or another SQL backend) - the JSON fallback does not support it");
        return false;
    }

    @Override
    public void close() {
        flush();
    }
}

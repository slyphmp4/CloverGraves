package com.slyph.clovergraves.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slyph.clovergraves.utils.CloverLogger;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
            CloverLogger.error("failed to read data.json - leaving it untouched, no graves were restored", ex);
            return result;
        }
        if (array == null) return result;

        int quarantined = 0;
        for (JsonElement element : array) {
            try {
                JsonObject object = element.getAsJsonObject();
                long id = nextId.getAndIncrement();
                GraveRecord record = new GraveRecord(
                        id,
                        UUID.fromString(object.get("owner").getAsString()),
                        object.has("ownerName") && !object.get("ownerName").isJsonNull() ? object.get("ownerName").getAsString() : null,
                        object.get("location").getAsString(),
                        Base64.getDecoder().decode(object.get("items").getAsString()),
                        object.has("dataVersion") ? object.get("dataVersion").getAsInt() : -1,
                        object.get("xp").getAsInt(),
                        object.get("date").getAsLong(),
                        null,
                        null
                );
                result.add(record);
                live.put(id, record);
                if (id >= nextId.get()) nextId.set(id + 1);
            } catch (Exception ex) {
                quarantined++;
                quarantine(element, ex);
            }
        }

        if (quarantined > 0) {
            CloverLogger.error("quarantined {} unreadable grave(s) from data.json into quarantine/ - see above for details", quarantined);
        }

        return result;
    }

    private void quarantine(@NotNull JsonElement entry, @NotNull Exception cause) {
        try {
            if (!quarantineDir.exists()) quarantineDir.mkdirs();
            File output = new File(quarantineDir, "grave-" + System.currentTimeMillis() + "-" + Math.abs(entry.hashCode()) + ".json");
            Files.writeString(output.toPath(), GSON.toJson(entry), StandardCharsets.UTF_8);
            CloverLogger.error("could not load a grave entry, quarantined to {}", output.getName(), cause);
        } catch (IOException ex) {
            CloverLogger.error("failed to quarantine an unreadable grave entry", ex);
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
            JsonObject object = new JsonObject();
            object.addProperty("owner", record.owner().toString());
            object.addProperty("ownerName", record.ownerName());
            object.addProperty("location", record.location());
            object.addProperty("items", Base64.getEncoder().encodeToString(record.items()));
            object.addProperty("dataVersion", record.dataVersion());
            object.addProperty("xp", record.storedXP());
            object.addProperty("date", record.createdAt());
            array.add(object);
        }

        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            Path temp = Files.createTempFile(dataFile.toPath().getParent(), "data", ".json.tmp");
            Files.writeString(temp, GSON.toJson(array), StandardCharsets.UTF_8);
            Files.move(temp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            CloverLogger.error("failed to save data.json", ex);
        }
    }

    @Override
    @NotNull
    public List<GraveRecord> history(@NotNull UUID owner, int limit) {
        return List.of();
    }

    @Override
    @NotNull
    public Optional<GraveRecord> historyEntry(long historyId) {
        return Optional.empty();
    }

    @Override
    public boolean claimForRestore(long historyId) {
        CloverLogger.warn("grave history/restore requires storage.type: H2 or another SQL backend - the JSON fallback does not support it");
        return false;
    }

    @Override
    public void close() {
        flush();
    }
}

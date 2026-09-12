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
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
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
    private boolean readFailed;

    public JsonGraveStorage(@NotNull File dataFolder) {
        this.dataFile = new File(dataFolder, "data.json");
        this.quarantineDir = new File(dataFolder, "quarantine");
    }

    @Override
    public void init() {
    }

    @Override
    @NotNull
    public synchronized List<GraveRecord> loadAll() {
        List<GraveRecord> result = new ArrayList<>();
        if (!dataFile.exists()) return result;

        JsonArray array;
        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            array = GSON.fromJson(json, JsonArray.class);
        } catch (Exception ex) {
            readFailed = true;
            throw new IllegalStateException("failed to read data.json; writes are disabled to preserve the file", ex);
        }
        if (array == null) {
            readFailed = true;
            throw new IllegalStateException("data.json must contain a JSON array; writes are disabled");
        }
        live.clear();
        nextId.set(1);
        readFailed = false;

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
    public synchronized long save(@NotNull GraveRecord record) {
        return saveAll(List.of(record)).getFirst();
    }

    @Override
    @NotNull
    public synchronized List<Long> saveAll(@NotNull List<GraveRecord> records) {
        Map<Long, GraveRecord> next = new HashMap<>(live);
        List<Long> ids = new ArrayList<>(records.size());
        for (GraveRecord record : records) {
            GraveRecord toStore = assignId(record);
            next.put(toStore.id(), toStore);
            ids.add(toStore.id());
        }
        if (!records.isEmpty()) {
            flush(next);
            live.clear();
            live.putAll(next);
        }
        return ids;
    }

    private GraveRecord assignId(@NotNull GraveRecord record) {
        if (record.id() > 0) nextId.accumulateAndGet(record.id() + 1, Math::max);
        return record.id() > 0 ? record : record.withId(nextId.getAndIncrement());
    }

    @Override
    public synchronized void remove(long id, @NotNull EndReason reason) {
        Map<Long, GraveRecord> next = new HashMap<>(live);
        next.remove(id);
        flush(next);
        live.remove(id);
    }

    private void flush(Map<Long, GraveRecord> records) {
        if (readFailed) throw new IllegalStateException("data.json is unreadable; refusing to overwrite it");
        JsonArray array = new JsonArray(records.size());
        for (GraveRecord record : records.values()) {
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

        Path temp = null;
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            temp = Files.createTempFile(dataFile.toPath().getParent(), "data", ".json.tmp");
            Files.writeString(temp, GSON.toJson(array), StandardCharsets.UTF_8);
            try {
                Files.move(temp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save data.json", ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                    CloverLogger.warn("failed to clean up temporary grave file {}", temp);
                }
            }
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
        // Every mutation is already persisted; never overwrite a file just by closing it.
    }
}

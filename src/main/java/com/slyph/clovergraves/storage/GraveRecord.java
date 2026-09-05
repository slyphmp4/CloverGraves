package com.slyph.clovergraves.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A persisted grave, decoupled from the live {@code Grave} object so storage backends never
 * need to touch Bukkit/NMS state directly - {@code items} is already-serialized NBT bytes (see
 * {@code GraveSnapshot}) and {@code location} is the AxAPI {@code Serializers.LOCATION} string
 * form, kept as an opaque string here so storage code has nothing Bukkit-specific to depend on.
 *
 * @param id        storage-assigned id; {@code <= 0} means "not yet assigned, assign one"
 * @param dataVersion the {@code Bukkit.getUnsafe().getDataVersion()} the items were serialized
 *                    under, so a future cross-version load failure can be diagnosed and
 *                    quarantined instead of silently discarding the whole batch
 */
public record GraveRecord(long id, @NotNull UUID owner, @Nullable String ownerName, @NotNull String location,
                           byte[] items, int dataVersion, int storedXP, long createdAt,
                           @Nullable EndReason endReason, @Nullable Long endedAt, boolean restored) {

    public GraveRecord(long id, @NotNull UUID owner, @Nullable String ownerName, @NotNull String location,
                        byte[] items, int dataVersion, int storedXP, long createdAt,
                        @Nullable EndReason endReason, @Nullable Long endedAt) {
        this(id, owner, ownerName, location, items, dataVersion, storedXP, createdAt, endReason, endedAt, false);
    }

    @NotNull
    public GraveRecord withId(long newId) {
        return new GraveRecord(newId, owner, ownerName, location, items, dataVersion, storedXP, createdAt, endReason, endedAt, restored);
    }

    @NotNull
    public GraveRecord ended(@NotNull EndReason reason, long at) {
        return new GraveRecord(id, owner, ownerName, location, items, dataVersion, storedXP, createdAt, reason, at, restored);
    }
}

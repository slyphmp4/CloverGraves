package com.artillexstudios.axgraves.grave;

/**
 * An immutable, cross-thread-safe view of a grave's contents at a point in time. This is the
 * only thing code outside the grave's owning region may read - {@code serializedItems} is
 * produced by {@code Serializers.ITEM_ARRAY.serialize(...)} on the owning region thread (inside
 * {@link GraveContents#refreshSnapshot()}), so consumers such as the async save/flush task and
 * the hologram-tracker-thread placeholders never touch a live Bukkit/NMS {@code ItemStack}.
 *
 * @param version         monotonically increasing; bumped on every content mutation
 * @param itemCount       number of non-null/non-air stacks
 * @param storedXP        xp currently held
 * @param serializedItems NBT-serialized items for this version
 * @param empty           true once there is nothing left to loot (items and XP both zero)
 */
public record GraveSnapshot(long version, int itemCount, int storedXP, byte[] serializedItems, boolean empty) {

    public static final GraveSnapshot INITIAL = new GraveSnapshot(-1, 0, 0, new byte[0], true);
}

package com.slyph.clovergraves.config;

import org.jetbrains.annotations.NotNull;

public record GraveSettings(int despawnTimeSeconds, boolean despawnWhenEmpty, boolean autoRotationEnabled,
                             float autoRotationSpeed, boolean dropItems, boolean droppedItemVelocity,
                             double interactRadius, boolean interactOnlyOwn, boolean enableInstantPickup,
                             boolean instantPickupOnlyOwn, boolean autoEquipArmor, int protectionSeconds,
                             int protectionMessageCooldownSeconds) {
    private static volatile GraveSettings current = defaults();

    public double interactRadiusSquared() {
        return interactRadius * interactRadius;
    }

    @NotNull
    public static GraveSettings current() {
        return current;
    }

    public static void reload(@NotNull CloverConfig config) {
        current = new GraveSettings(
                config.getInt("despawn-time-seconds", 1800),
                config.getBoolean("despawn-when-empty", true),
                config.getBoolean("auto-rotation.enabled", false),
                config.getFloat("auto-rotation.speed", 10f),
                config.getBoolean("drop-items", true),
                config.getBoolean("dropped-item-velocity", true),
                config.getDouble("interact-radius", 7.0),
                config.getBoolean("interact-only-own", false),
                config.getBoolean("enable-instant-pickup", true),
                config.getBoolean("instant-pickup-only-own", false),
                config.getBoolean("auto-equip-armor", true),
                config.getInt("protection.seconds", 30),
                config.getInt("protection.message-cooldown-seconds", 3)
        );
    }

    @NotNull
    private static GraveSettings defaults() {
        return new GraveSettings(1800, true, false, 10f, true, true, 7.0, false, true, false, true, 30, 3);
    }
}

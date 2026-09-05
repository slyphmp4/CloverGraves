package com.slyph.clovergraves.config;

import org.jetbrains.annotations.NotNull;

public record GraveSettings(int despawnTimeSeconds, boolean autoRotationEnabled, float autoRotationSpeed,
                             boolean dropItems, boolean droppedItemVelocity, double interactRadius,
                             float interactionHitboxWidth, float interactionHitboxHeight,
                             boolean interactOnlyOwn, boolean enableInstantPickup, boolean instantPickupOnlyOwn,
                             boolean autoEquipArmor, int protectionSeconds, int protectionMessageCooldownSeconds) {
    private static final float MIN_HITBOX_SIZE = 0.2f;
    private static final float MAX_HITBOX_SIZE = 4.0f;
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
                config.getBoolean("auto-rotation.enabled", false),
                config.getFloat("auto-rotation.speed", 10f),
                config.getBoolean("drop-items", true),
                config.getBoolean("dropped-item-velocity", true),
                Math.max(0.5, config.getDouble("interact-radius", 7.0)),
                clampHitbox(config.getFloat("interaction-hitbox.width", 1.6f)),
                clampHitbox(config.getFloat("interaction-hitbox.height", 2.2f)),
                config.getBoolean("interact-only-own", false),
                config.getBoolean("enable-instant-pickup", true),
                config.getBoolean("instant-pickup-only-own", false),
                config.getBoolean("auto-equip-armor", true),
                Math.max(0, config.getInt("protection.seconds", 30)),
                Math.max(0, config.getInt("protection.message-cooldown-seconds", 3))
        );
    }

    private static float clampHitbox(float value) {
        return Math.max(MIN_HITBOX_SIZE, Math.min(MAX_HITBOX_SIZE, value));
    }

    @NotNull
    private static GraveSettings defaults() {
        return new GraveSettings(1800, false, 10f, true, true, 7.0, 1.6f, 2.2f,
                false, true, false, true, 30, 3);
    }
}

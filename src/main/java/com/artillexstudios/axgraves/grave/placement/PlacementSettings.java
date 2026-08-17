package com.artillexstudios.axgraves.grave.placement;

public record PlacementSettings(boolean enabled, boolean avoidLava, boolean avoidSolid, boolean avoidNetherRoof,
                                 int netherRoofY, int maxHorizontalRadius, int maxVerticalDistance, int minY,
                                 int maxY, boolean notifyOwner) {

    /** {@code max-horizontal-radius} is clamped to this regardless of config, to bound worst-case probe cost. */
    public static final int HARD_RADIUS_CAP = 16;

    public PlacementSettings {
        maxHorizontalRadius = Math.min(Math.max(maxHorizontalRadius, 0), HARD_RADIUS_CAP);
        maxVerticalDistance = Math.max(maxVerticalDistance, 0);
    }
}

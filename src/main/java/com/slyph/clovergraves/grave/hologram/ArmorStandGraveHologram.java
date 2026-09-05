package com.slyph.clovergraves.grave.hologram;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArmorStandGraveHologram implements GraveHologram {
    private static final double NAME_OFFSET = 0.35;

    private final Location topLocation;
    private final float lineSpacing;
    private final List<ArmorStand> stands = new ArrayList<>();
    private boolean removed;

    public ArmorStandGraveHologram(@NotNull Location topLocation, @NotNull List<String> lines, float lineSpacing) {
        this.topLocation = topLocation.clone();
        this.lineSpacing = lineSpacing;
        rebuild(lines);
    }

    @Override
    public void setLines(@NotNull List<String> lines) {
        if (removed || stands.size() != lines.size()) {
            rebuild(lines);
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (stand.isDead()) {
                rebuild(lines);
                return;
            }

            String line = lines.get(i);
            if (!Objects.equals(stand.getCustomName(), line)) {
                stand.setCustomNameVisible(false);
                stand.setCustomName(null);
                stand.setCustomName(line);
                stand.setCustomNameVisible(true);
            }
        }
    }

    private void rebuild(@NotNull List<String> lines) {
        removeStands();
        removed = false;

        for (int i = 0; i < lines.size(); i++) {
            Location lineLocation = topLocation.clone().add(0, -i * lineSpacing - NAME_OFFSET, 0);
            ArmorStand stand = (ArmorStand) topLocation.getWorld().spawnEntity(lineLocation, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            try {
                stand.setMarker(true);
            } catch (Throwable ignored) {
            }
            stand.setCustomName(lines.get(i));
            stand.setCustomNameVisible(true);
            stands.add(stand);
        }
    }

    @Override
    public boolean isValid() {
        if (removed) return false;
        for (ArmorStand stand : stands) {
            if (stand.isDead()) return false;
        }
        return true;
    }

    @Override
    public void remove() {
        removed = true;
        removeStands();
    }

    private void removeStands() {
        for (ArmorStand stand : stands) {
            try {
                stand.remove();
            } catch (Throwable ignored) {
            }
        }
        stands.clear();
    }
}

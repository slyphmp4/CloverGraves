package com.slyph.clovergraves.grave.hologram;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ArmorStandGraveHologram implements GraveHologram {
    private static final double NAME_OFFSET = 0.35;

    private final Location topLocation;
    private final float lineSpacing;
    private final List<ArmorStand> stands = new ArrayList<>();

    public ArmorStandGraveHologram(@NotNull Location topLocation, @NotNull List<String> lines, float lineSpacing) {
        this.topLocation = topLocation.clone();
        this.lineSpacing = lineSpacing;
        rebuild(lines);
    }

    @Override
    public void setLines(@NotNull List<String> lines) {
        if (stands.size() != lines.size()) {
            rebuild(lines);
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (!stand.isValid()) {
                rebuild(lines);
                return;
            }
            stand.setCustomName(lines.get(i));
            stand.setCustomNameVisible(true);
        }
    }

    private void rebuild(@NotNull List<String> lines) {
        remove();
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
        for (ArmorStand stand : stands) {
            if (!stand.isValid()) return false;
        }
        return true;
    }

    @Override
    public void remove() {
        for (ArmorStand stand : stands) {
            try {
                stand.remove();
            } catch (Throwable ignored) {
            }
        }
        stands.clear();
    }
}

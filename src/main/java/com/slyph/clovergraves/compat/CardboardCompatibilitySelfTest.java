package com.slyph.clovergraves.compat;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.grave.hologram.TextDisplayGraveHologram;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.utils.CloverLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CardboardCompatibilitySelfTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private CardboardCompatibilitySelfTest() {
    }

    public static void run() {
        List<Entity> spawned = new ArrayList<>();
        TextDisplayGraveHologram hologram = null;
        try {
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("compatibility test is not on the server thread");
            World world = Bukkit.getWorlds().stream().findFirst().orElseThrow();
            Location location = world.getSpawnLocation().clone().add(0.5, 3, 0.5);

            ItemStack[] source = {new ItemStack(Material.STONE, 3), new ItemStack(Material.DIAMOND, 1)};
            ItemStack[] restored = ItemSerialization.deserialize(ItemSerialization.serialize(source));
            if (restored.length != 2 || restored[0] == null || restored[0].getType() != Material.STONE || restored[0].getAmount() != 3) {
                throw new IllegalStateException("Bukkit item serialization round-trip failed");
            }

            ArmorStand armorStand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
            spawned.add(armorStand);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setPersistent(false);
            armorStand.setInvulnerable(true);
            if (armorStand.getEquipment() != null) armorStand.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            armorStand.addDisabledSlots(EquipmentSlot.HEAD);
            armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.ADDING_OR_CHANGING);
            armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
            if (!armorStand.isSlotDisabled(EquipmentSlot.HEAD)) {
                throw new IllegalStateException("ArmorStand head slot is not disabled");
            }
            if (!armorStand.hasEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING)) {
                throw new IllegalStateException("ArmorStand head removal lock failed");
            }

            TextDisplay entityTypeDisplay = (TextDisplay) world.spawnEntity(location.clone().add(0, 1, 0), EntityType.TEXT_DISPLAY);
            spawned.add(entityTypeDisplay);
            entityTypeDisplay.setPersistent(false);
            entityTypeDisplay.text(Component.text("EntityType route"));
            if (!"EntityType route".equals(PLAIN.serialize(entityTypeDisplay.text()))) {
                throw new IllegalStateException("EntityType.TEXT_DISPLAY route failed");
            }

            HologramSettings settings = HologramSettings.parse(null);
            hologram = new TextDisplayGraveHologram(
                    location.clone().add(0, 2, 0),
                    List.of(Component.text("CloverGraves"), Component.text("00:29:01")),
                    settings,
                    0.3f
            );
            if (!hologram.isValid()) throw new IllegalStateException("TextDisplay hologram failed to spawn");

            TextDisplay display = hologram.display();
            UUID displayId = display.getUniqueId();
            if (display.getAlignment() != settings.alignment()) throw new IllegalStateException("TextDisplay alignment failed");
            if (display.getBillboard() != settings.billboard()) throw new IllegalStateException("TextDisplay billboard failed");
            if (display.isSeeThrough() != settings.seeThrough()) throw new IllegalStateException("TextDisplay see-through failed");
            if (display.isShadowed() != settings.shadow()) throw new IllegalStateException("TextDisplay shadow failed");
            if (display.isDefaultBackground()) throw new IllegalStateException("TextDisplay custom background was not enabled");
            if (display.getLineWidth() != 1000) throw new IllegalStateException("TextDisplay line width failed");
            if (display.getInterpolationDelay() != 0) throw new IllegalStateException("TextDisplay interpolation delay is not zero");
            if (display.getInterpolationDuration() != 0) throw new IllegalStateException("TextDisplay interpolation duration is not zero");
            if (display.getTeleportDuration() != 0) throw new IllegalStateException("TextDisplay teleport duration is not zero");
            Color background = display.getBackgroundColor();
            if (background == null || background.asARGB() != settings.backgroundColor()) {
                throw new IllegalStateException("TextDisplay background failed");
            }

            String[] timerValues = {"00:29:00", "00:28:59", "00:28:58"};
            for (String timer : timerValues) {
                hologram.setLines(List.of(Component.text("CloverGraves"), Component.text(timer)));
                display = hologram.display();
                if (!displayId.equals(display.getUniqueId())) {
                    throw new IllegalStateException("TextDisplay entity was replaced during timer update");
                }
                if (!("CloverGraves\n" + timer).equals(PLAIN.serialize(display.text()))) {
                    throw new IllegalStateException("TextDisplay timer update failed at " + timer);
                }
                if (!hologram.isValid()) throw new IllegalStateException("TextDisplay hologram became invalid after timer update");
            }

            Bukkit.createInventory(null, 9, "CloverGraves Test");
            CloverLogger.info("CLOVERGRAVES_CARDBOARD_26_2_SELFTEST_PASS");
        } catch (Throwable throwable) {
            CloverLogger.error("CLOVERGRAVES_CARDBOARD_26_2_SELFTEST_FAIL", throwable);
            Bukkit.getPluginManager().disablePlugin(AxGraves.getInstance());
        } finally {
            if (hologram != null) {
                try {
                    hologram.remove();
                } catch (Throwable ignored) {
                }
            }
            for (Entity entity : spawned) {
                try {
                    entity.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}

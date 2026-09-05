package com.slyph.clovergraves.compat;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.grave.hologram.TextDisplayGraveHologram;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.utils.CloverLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CardboardCompatibilitySelfTest {
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

            TextDisplay entityTypeDisplay = (TextDisplay) world.spawnEntity(location.clone().add(0, 1, 0), EntityType.TEXT_DISPLAY);
            spawned.add(entityTypeDisplay);
            entityTypeDisplay.setPersistent(false);
            entityTypeDisplay.text(Component.text("EntityType route"));
            if (!Component.text("EntityType route").equals(entityTypeDisplay.text())) {
                throw new IllegalStateException("EntityType.TEXT_DISPLAY route failed");
            }

            HologramSettings settings = HologramSettings.parse(null);
            hologram = new TextDisplayGraveHologram(
                    location.clone().add(0, 2, 0),
                    List.of(Component.text("CloverGraves"), Component.text("Cardboard 26.2")),
                    settings,
                    0.3f
            );
            if (!hologram.isValid()) throw new IllegalStateException("TextDisplay hologram failed to spawn");

            TextDisplay display = hologram.display();
            if (display.getAlignment() != settings.alignment()) throw new IllegalStateException("TextDisplay alignment failed");
            if (display.getBillboard() != settings.billboard()) throw new IllegalStateException("TextDisplay billboard failed");
            if (display.isSeeThrough() != settings.seeThrough()) throw new IllegalStateException("TextDisplay see-through failed");
            if (display.isShadowed() != settings.shadow()) throw new IllegalStateException("TextDisplay shadow failed");
            if (display.isDefaultBackground()) throw new IllegalStateException("TextDisplay custom background was not enabled");
            Color background = display.getBackgroundColor();
            if (background == null || background.asARGB() != settings.backgroundColor()) {
                throw new IllegalStateException("TextDisplay background failed");
            }

            Component updated = Component.text("CloverGraves").append(Component.newline()).append(Component.text("Cardboard 26.2 updated"));
            hologram.setLines(List.of(Component.text("CloverGraves"), Component.text("Cardboard 26.2 updated")));
            if (!updated.equals(display.text())) throw new IllegalStateException("TextDisplay Adventure text update failed");
            if (!hologram.isValid()) throw new IllegalStateException("TextDisplay hologram became invalid after update");

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

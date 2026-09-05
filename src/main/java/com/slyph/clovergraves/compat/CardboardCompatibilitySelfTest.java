package com.slyph.clovergraves.compat;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.config.HologramSettings;
import com.slyph.clovergraves.grave.hologram.GraveHologram;
import com.slyph.clovergraves.grave.hologram.GraveHologramFactory;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.utils.CloverLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CardboardCompatibilitySelfTest {
    private CardboardCompatibilitySelfTest() {
    }

    public static void run() {
        List<Entity> spawned = new ArrayList<>();
        GraveHologram hologram = null;
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

            hologram = GraveHologramFactory.create(
                    location.clone().add(0, 1, 0),
                    List.of("CloverGraves", "Cardboard 26.2"),
                    HologramSettings.parse(null),
                    0.3f
            );
            if (!hologram.isValid()) throw new IllegalStateException("hologram backend failed to spawn");

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

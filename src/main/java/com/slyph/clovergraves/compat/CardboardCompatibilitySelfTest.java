package com.slyph.clovergraves.compat;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.storage.ItemSerialization;
import com.slyph.clovergraves.utils.CloverLogger;
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
            armorStand.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));

            TextDisplay display = (TextDisplay) world.spawnEntity(location.clone().add(0, 1, 0), EntityType.TEXT_DISPLAY);
            spawned.add(display);
            display.setPersistent(false);
            display.setText("CloverGraves Cardboard 26.2 test");
            display.setBackgroundColor(Color.fromARGB(0));
            display.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            display.setSeeThrough(false);
            display.setShadowed(true);

            Bukkit.createInventory(null, 9, "CloverGraves Test");
            CloverLogger.info("CLOVERGRAVES_CARDBOARD_26_2_SELFTEST_PASS");
        } catch (Throwable throwable) {
            CloverLogger.error("CLOVERGRAVES_CARDBOARD_26_2_SELFTEST_FAIL", throwable);
            Bukkit.getPluginManager().disablePlugin(AxGraves.getInstance());
        } finally {
            for (Entity entity : spawned) {
                try {
                    entity.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}

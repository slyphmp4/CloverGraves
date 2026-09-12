package com.slyph.clovergraves.listeners;

import com.slyph.clovergraves.grave.ChunkKey;
import com.slyph.clovergraves.grave.Grave;
import com.slyph.clovergraves.grave.SpawnedGraves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.jetbrains.annotations.NotNull;

public final class GraveChunkListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        ChunkKey key = ChunkKey.of(event.getChunk());
        for (Grave grave : SpawnedGraves.getGraves(key)) grave.onChunkLoaded();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        ChunkKey key = ChunkKey.of(event.getChunk());
        for (Grave grave : SpawnedGraves.getGraves(key)) grave.despawnVisuals();
    }
}

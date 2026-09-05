package com.slyph.clovergraves.schedulers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

public final class CloverScheduler {
    private static volatile CloverScheduler instance;

    private final JavaPlugin plugin;

    private CloverScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(@NotNull JavaPlugin plugin) {
        instance = new CloverScheduler(plugin);
    }

    @NotNull
    public static CloverScheduler get() {
        CloverScheduler scheduler = instance;
        if (scheduler == null) throw new IllegalStateException("CloverScheduler is not initialized");
        return scheduler;
    }

    public CloverTask run(@NotNull Runnable runnable) {
        return scheduleSync(0L, -1L, task -> runnable.run());
    }

    public CloverTask run(@NotNull Consumer<CloverTask> consumer) {
        return scheduleSync(0L, -1L, consumer);
    }

    public CloverTask run(@NotNull Entity entity, @NotNull Consumer<CloverTask> consumer, @NotNull Runnable retired) {
        return scheduleEntity(entity, 0L, consumer, retired);
    }

    public CloverTask runAt(@NotNull Location location, @NotNull Runnable runnable) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return scheduleSync(0L, -1L, task -> runnable.run());
    }

    public CloverTask runAt(@NotNull Location location, @NotNull Consumer<CloverTask> consumer) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return scheduleSync(0L, -1L, consumer);
    }

    public CloverTask runLaterAt(@NotNull Location location, @NotNull Consumer<CloverTask> consumer, long delay) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return scheduleSync(Math.max(0L, delay), -1L, consumer);
    }

    public CloverTask runTimerAt(@NotNull Location location, @NotNull Runnable runnable, long delay, long period) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return scheduleSync(Math.max(0L, delay), Math.max(1L, period), task -> runnable.run());
    }

    public CloverTask runLater(@NotNull Entity entity, @NotNull Consumer<CloverTask> consumer, @NotNull Runnable retired, long delay) {
        return scheduleEntity(entity, Math.max(0L, delay), consumer, retired);
    }

    public CloverTask runAsync(@NotNull Runnable runnable) {
        return scheduleAsync(0L, -1L, task -> runnable.run());
    }

    public CloverTask runLaterAsync(@NotNull Consumer<CloverTask> consumer, long delay) {
        return scheduleAsync(Math.max(0L, delay), -1L, consumer);
    }

    public CloverTask runAsyncTimer(@NotNull Consumer<CloverTask> consumer, long delay, long period) {
        return scheduleAsync(Math.max(0L, delay), Math.max(1L, period), consumer);
    }

    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        return Bukkit.isPrimaryThread();
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private CloverTask scheduleEntity(Entity entity, long delay, Consumer<CloverTask> consumer, Runnable retired) {
        return scheduleSync(delay, -1L, task -> {
            if (!entity.isValid()) {
                retired.run();
                return;
            }
            consumer.accept(task);
        });
    }

    private CloverTask scheduleSync(long delay, long period, Consumer<CloverTask> consumer) {
        CloverTask wrapper = new CloverTask();
        Runnable runnable = () -> {
            if (!wrapper.isCancelled()) consumer.accept(wrapper);
        };

        BukkitTask task;
        if (period > 0L) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        } else if (delay > 0L) {
            task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        } else {
            task = Bukkit.getScheduler().runTask(plugin, runnable);
        }

        wrapper.bind(task);
        return wrapper;
    }

    private CloverTask scheduleAsync(long delay, long period, Consumer<CloverTask> consumer) {
        CloverTask wrapper = new CloverTask();
        Runnable runnable = () -> {
            if (!wrapper.isCancelled()) consumer.accept(wrapper);
        };

        BukkitTask task;
        if (period > 0L) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period);
        } else if (delay > 0L) {
            task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
        } else {
            task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }

        wrapper.bind(task);
        return wrapper;
    }
}

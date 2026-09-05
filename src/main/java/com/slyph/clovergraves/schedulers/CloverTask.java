package com.slyph.clovergraves.schedulers;

import org.bukkit.scheduler.BukkitTask;

public final class CloverTask {
    private volatile BukkitTask delegate;
    private volatile boolean cancelled;

    void bind(BukkitTask task) {
        this.delegate = task;
        if (cancelled) task.cancel();
    }

    public void cancel() {
        cancelled = true;
        BukkitTask task = delegate;
        if (task != null) task.cancel();
    }

    public boolean isCancelled() {
        BukkitTask task = delegate;
        return cancelled || task != null && task.isCancelled();
    }
}

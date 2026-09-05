package com.slyph.clovergraves.grave.hologram;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GraveHologram {
    void setLines(@NotNull List<String> lines);

    boolean isValid();

    void remove();
}

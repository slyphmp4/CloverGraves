package com.slyph.clovergraves.grave.hologram;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GraveHologram {
    void setLines(@NotNull List<Component> lines);

    boolean isValid();

    void remove();
}

package com.slyph.clovergraves.storage;

import com.artillexstudios.axapi.dependencies.DependencyManagerWrapper;
import org.jetbrains.annotations.NotNull;

public final class SqlDrivers {
    public static final String H2_RELOCATION = "com.slyph.clovergraves.libs.h2";
    public static final String SQLITE_RELOCATION = "com.slyph.clovergraves.libs.sqlite";

    private static final String H2_VERSION = "2.3.232";
    private static final String SQLITE_VERSION = "3.46.1.3";

    private SqlDrivers() {
    }

    public static void declare(@NotNull DependencyManagerWrapper wrapper) {
        wrapper.repository("https://repo1.maven.org/maven2/");

        wrapper.dependency("com.h2database:h2:" + H2_VERSION);
        wrapper.relocate("org.h2", H2_RELOCATION);

        wrapper.dependency("org.xerial:sqlite-jdbc:" + SQLITE_VERSION);
        wrapper.relocate("org.sqlite", SQLITE_RELOCATION);
    }
}

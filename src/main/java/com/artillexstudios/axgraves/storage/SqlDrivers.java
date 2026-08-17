package com.artillexstudios.axgraves.storage;

import com.artillexstudios.axapi.dependencies.DependencyManagerWrapper;
import org.jetbrains.annotations.NotNull;
import revxrsal.zapper.Dependency;
import revxrsal.zapper.repository.Repository;

/**
 * Runtime-fetched JDBC drivers for the embedded {@code storage.type} options (H2, SQLite) -
 * declared via {@code AxPlugin#dependencies(...)} rather than shaded into the jar, since not
 * every server needs either one. Relocated so they can't collide with another plugin's copy of
 * the same driver on a shared classloader.
 *
 * <p>MySQL is intentionally not auto-fetched here: servers that use MySQL typically already
 * carry a driver on the classpath (bundled with the server jar or another plugin). If
 * {@code storage.type: MYSQL} is set and no driver is available, storage initialization fails
 * and {@code AxGraves} falls back to the JSON backend.</p>
 */
public final class SqlDrivers {
    public static final String H2_RELOCATION = "com.artillexstudios.axgraves.libs.h2";
    public static final String SQLITE_RELOCATION = "com.artillexstudios.axgraves.libs.sqlite";

    private static final String H2_VERSION = "2.3.232";
    private static final String SQLITE_VERSION = "3.46.1.3";

    private SqlDrivers() {
    }

    public static void declare(@NotNull DependencyManagerWrapper wrapper) {
        wrapper.repository(Repository.mavenCentral());

        wrapper.dependency(new Dependency("com.h2database", "h2", H2_VERSION));
        wrapper.relocate("org.h2", H2_RELOCATION);

        wrapper.dependency(new Dependency("org.xerial", "sqlite-jdbc", SQLITE_VERSION));
        wrapper.relocate("org.sqlite", SQLITE_RELOCATION);
    }
}

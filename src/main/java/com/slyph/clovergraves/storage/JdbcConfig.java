package com.slyph.clovergraves.storage;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public record JdbcConfig(@NotNull Type type, @NotNull String url, @NotNull String username,
                         @NotNull String password, @NotNull String tablePrefix) {

    public enum Type {
        H2,
        SQLITE,
        MYSQL
    }

    public Connection open() throws SQLException {
        ensureDriver();
        if (username.isBlank() && password.isBlank()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, username, password);
    }

    private void ensureDriver() throws SQLException {
        String driver = switch (type) {
            case H2 -> "org.h2.Driver";
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
        };

        try {
            Class.forName(driver, true, JdbcConfig.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new SQLException("Missing JDBC driver " + driver, ex);
        }
    }
}

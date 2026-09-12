package com.slyph.clovergraves.storage;

public record JdbcPoolConfig(int maximumPoolSize, int minimumIdle, long connectionTimeoutMillis) {
    public JdbcPoolConfig {
        maximumPoolSize = Math.max(1, maximumPoolSize);
        minimumIdle = Math.clamp(minimumIdle, 0, maximumPoolSize);
        connectionTimeoutMillis = Math.max(250L, connectionTimeoutMillis);
    }

    public static JdbcPoolConfig defaults() {
        return new JdbcPoolConfig(4, 1, 5_000L);
    }
}

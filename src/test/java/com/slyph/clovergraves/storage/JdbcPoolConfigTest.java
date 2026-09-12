package com.slyph.clovergraves.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcPoolConfigTest {
    @Test
    void invalidPoolValuesAreClampedToSafeBounds() {
        JdbcPoolConfig config = new JdbcPoolConfig(0, 99, 10);
        assertEquals(1, config.maximumPoolSize());
        assertEquals(1, config.minimumIdle());
        assertEquals(250L, config.connectionTimeoutMillis());
    }

    @Test
    void defaultsStaySmallForMinecraftStorageWorkloads() {
        JdbcPoolConfig config = JdbcPoolConfig.defaults();
        assertEquals(4, config.maximumPoolSize());
        assertEquals(1, config.minimumIdle());
        assertEquals(5_000L, config.connectionTimeoutMillis());
    }
}

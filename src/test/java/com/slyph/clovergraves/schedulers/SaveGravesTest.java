package com.slyph.clovergraves.schedulers;

import com.slyph.clovergraves.grave.SpawnedGraves;
import com.slyph.clovergraves.storage.EndReason;
import com.slyph.clovergraves.storage.GraveStorage;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SaveGravesTest {
    @Test
    void failedDeletionIsRetriedOnNextFlushWithoutAnInfiniteLoop() {
        AtomicInteger calls = new AtomicInteger();
        GraveStorage storage = (GraveStorage) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{GraveStorage.class}, (proxy, method, args) -> {
                    assertEquals("remove", method.getName());
                    assertEquals(42L, args[0]);
                    assertEquals(EndReason.LOOTED, args[1]);
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("database offline");
                    return null;
                });
        SpawnedGraves.retryRemoval(new SpawnedGraves.PendingRemoval(42, EndReason.LOOTED));
        SaveGraves.flushRemovals(storage);
        assertEquals(1, calls.get());
        SaveGraves.flushRemovals(storage);
        assertEquals(2, calls.get());
        assertNull(SpawnedGraves.pollRemoval());
    }
}

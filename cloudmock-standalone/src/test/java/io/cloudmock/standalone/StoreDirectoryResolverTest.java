package io.cloudmock.standalone;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreDirectoryResolverTest {

    @Test
    void defaultsToPersistentStoreDirectory() {
        assertEquals(Path.of(StoreDirectoryResolver.DEFAULT_STORE_DIR),
                StoreDirectoryResolver.resolve(new String[] {"--port=4566"}));
    }

    @Test
    void parsesEqualsFormFlag() {
        assertEquals(Path.of("/tmp/cm-state"),
                StoreDirectoryResolver.resolve(new String[] {"--store-dir=/tmp/cm-state"}));
    }

    @Test
    void parsesSpaceSeparatedFlag() {
        assertEquals(Path.of("state-dir"),
                StoreDirectoryResolver.resolve(new String[] {"--store-dir", "state-dir"}));
    }

    @Test
    void noneSelectsInMemoryStore() {
        assertNull(StoreDirectoryResolver.resolve(new String[] {"--store-dir=none"}));
    }

    @Test
    void offSelectsInMemoryStore() {
        assertNull(StoreDirectoryResolver.resolve(new String[] {"--store-dir=off"}));
    }

    @Test
    void blankValueSelectsInMemoryStore() {
        assertNull(StoreDirectoryResolver.resolve(new String[] {"--store-dir="}));
    }
}

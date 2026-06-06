package io.cloudmock.core.internal.store;

import io.cloudmock.core.spi.StateStore;

import java.nio.file.Path;

/**
 * Chooses the {@link StateStore} implementation for a CloudMock instance: a persistent
 * JSON-file store when a directory is configured, otherwise a throwaway in-memory store.
 */
public final class StateStoreFactory {

    private StateStoreFactory() {}

    /**
     * @param storeDirectory directory for persistent state, or {@code null} for in-memory
     * @return a persistent {@link JsonFileStateStore} when {@code storeDirectory} is set,
     *         otherwise an {@link InMemoryStateStore}
     */
    public static StateStore create(Path storeDirectory) {
        return storeDirectory != null
                ? new JsonFileStateStore(storeDirectory)
                : new InMemoryStateStore();
    }
}

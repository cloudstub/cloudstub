package io.cloudmock.core.internal.store;

import io.cloudmock.core.StatePersistence;
import io.cloudmock.core.spi.StateStore;

import java.nio.file.Path;

/**
 * Chooses the {@link StateStore} implementation for a CloudMock instance: a persistent store of the
 * requested {@link StatePersistence} backend when a directory is configured, otherwise a throwaway
 * in-memory store.
 */
public final class StateStoreFactory {

    private StateStoreFactory() {}

    /**
     * @param storeDirectory directory for persistent state, or {@code null} for in-memory
     * @param backend        which persistent backend to use when {@code storeDirectory} is set
     * @return an {@link InMemoryStateStore} when {@code storeDirectory} is {@code null}; otherwise
     *         the persistent store selected by {@code backend}
     */
    public static StateStore create(Path storeDirectory, StatePersistence backend) {
        if (storeDirectory == null) {
            return new InMemoryStateStore();
        }
        return switch (backend) {
            case APPEND_LOG -> new AppendLogStateStore(storeDirectory);
            case JSON_FILE -> new JsonFileStateStore(storeDirectory);
        };
    }
}

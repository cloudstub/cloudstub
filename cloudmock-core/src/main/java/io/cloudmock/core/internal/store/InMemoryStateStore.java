package io.cloudmock.core.internal.store;

import io.cloudmock.core.spi.StateStore;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link StateStore}. State is lost when the JVM exits.
 * Used as the default store in embedded test mode.
 */
public final class InMemoryStateStore implements StateStore {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        data.put(key, value);
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public List<String> list(String prefix) {
        return StateStoreSupport.keysWithPrefix(data.keySet(), prefix);
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public void clear(String prefix) {
        StateStoreSupport.removeKeysWithPrefix(data, prefix);
    }

    @Override
    public void clearAll() {
        data.clear();
    }
}

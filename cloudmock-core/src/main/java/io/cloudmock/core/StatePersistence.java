package io.cloudmock.core;

/**
 * Selects the persistent {@link io.cloudmock.core.spi.StateStore} backend used when a store
 * directory is configured via {@link CloudMock#withStoreDirectory}.
 *
 * <p>The choice only affects how state is written to disk; both backends preserve value types
 * across a restart and are thread-safe. With no store directory the backend is irrelevant — state
 * is held in memory and lost on stop.
 */
public enum StatePersistence {

    /**
     * Append-only log with periodic compaction (the default). Each mutation appends a single record,
     * so write cost scales with the change rather than the size of the whole store, and a burst of
     * writes is not quadratic in I/O.
     */
    APPEND_LOG,

    /**
     * Single JSON document rewritten in full on every mutation. Simple and human-readable, but a
     * single write is O(store size); retained as an explicit choice for small or static state.
     */
    JSON_FILE
}

package io.cloudmock.core.spi;

import java.util.List;

/**
 * Shared key-value backend owned and lifecycle-managed by the core engine.
 *
 * <p>Modules read from and write to the store so that one AWS SDK call can affect the response
 * to a later call. The store has no AWS knowledge — modules are responsible for translating
 * between the AWS API protocol and store operations.
 *
 * <p>Keys follow a path-like hierarchy: {@code {serviceId}/{resource-type}/{name}[/{subresource}/{id}]}
 * For example:
 * <ul>
 *   <li>{@code sqs/queues/my-queue/messages/abc-123}
 *   <li>{@code s3/buckets/my-bucket/objects/my-key}
 *   <li>{@code secrets/my-secret}
 * </ul>
 *
 * <p>The store does not enforce key prefixing — scoping by service ID is a module-authoring convention.
 *
 * <p>The admin REST API, CLI, and management console read from the store via {@link #get} and
 * {@link #list}. They never write directly — all writes flow through module code triggered by
 * AWS SDK calls.
 *
 * <p>Implementations must be thread-safe. The AWS SDK may fire concurrent requests and multiple
 * modules share the same store instance.
 *
 * <p>Values must be JSON-serialisable. A value is read back as its original concrete type, including
 * after a restart of a persistent store, so the type a module {@code put}s is the type it can cast
 * the result of {@code get} to.
 */
public interface StateStore {

    /**
     * Store a value under {@code key}. Overwrites any existing entry at the same key.
     *
     * @param key   path-style key, e.g. {@code "sqs/queues/my-queue/messages/abc-123"}
     * @param value the value to store; must be JSON-serialisable
     * @throws NullPointerException if {@code key} or {@code value} is null
     * @throws io.cloudmock.core.exception.CloudMockStateException if a persistent store fails to
     *         write to disk
     */
    void put(String key, Object value);

    /**
     * Retrieve the value stored under {@code key}, or {@code null} if the key does not exist.
     *
     * @param key path-style key
     * @return the stored value, or {@code null}
     */
    Object get(String key);

    /**
     * List all keys whose path begins with {@code prefix}.
     * Passing {@code "sqs/"} lists every key the SQS module has written.
     *
     * @param prefix key prefix to match
     * @return sorted list of matching keys; empty if none match
     */
    List<String> list(String prefix);

    /**
     * Delete a single entry. No-op if the key does not exist.
     *
     * @param key the key to delete
     */
    void delete(String key);

    /**
     * Delete all entries whose key begins with {@code prefix}.
     * Passing {@code "sqs/"} clears all SQS state.
     *
     * @param prefix the key prefix to clear
     */
    void clear(String prefix);

    /**
     * Delete all entries in the store across all services.
     * Called by the global reset endpoint.
     */
    void clearAll();
}

package io.cloudmock.core.spi;

/**
 * Entry point that every CloudMock service module must implement.
 *
 * <p>Modules are discovered at runtime via {@link java.util.ServiceLoader}. Each module JAR must
 * contain a {@code META-INF/services/io.cloudmock.core.spi.CloudMockService} file listing its
 * implementation class. The core engine calls {@link #register(CloudMockContext)} once on startup for
 * every discovered implementation.
 *
 * <h2>Compatibility</h2>
 * <p>Module JARs must declare the minimum {@code cloudmock-core} version they require via the
 * {@code CloudMock-Core-Min-Version} entry in their {@code MANIFEST.MF}. The core engine reads this
 * attribute at startup and logs a warning if the running core version is older. Example:
 * <pre>
 * CloudMock-Core-Min-Version: 0.1.0
 * </pre>
 *
 * <h2>Threading</h2>
 * <p>{@link #register(CloudMockContext)} is called from a single thread during startup. Implementations
 * do not need to be thread-safe.
 */
public interface CloudMockService {

    /**
     * Unique, lowercase identifier for the AWS service this module handles.
     * Used for logging and diagnostic output only — it does not affect routing.
     *
     * @return service identifier, e.g. {@code "sqs"}, {@code "secretsmanager"}, {@code "s3"}
     */
    String serviceId();

    /**
     * Registers all stubs for this service and receives the shared state store.
     * Called exactly once per CloudMock lifecycle, before any test traffic arrives.
     *
     * @param context carries the stub registrar and the shared state store
     */
    void register(CloudMockContext context);
}

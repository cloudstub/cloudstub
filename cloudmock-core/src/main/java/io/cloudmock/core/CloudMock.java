package io.cloudmock.core;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudmock.core.exception.CloudMockAlreadyStartedException;
import io.cloudmock.core.exception.CloudMockNotStartedException;
import io.cloudmock.core.internal.AwsEndpointOverride;
import io.cloudmock.core.internal.CloudMockResponseTransformer;
import io.cloudmock.core.internal.CloudMockSettings;
import io.cloudmock.core.internal.FaultEngine;
import io.cloudmock.core.internal.ModuleInitializer;
import io.cloudmock.core.internal.RequestHistory;
import io.cloudmock.core.internal.ServiceRegistry;
import io.cloudmock.core.internal.WireMockServerFactory;
import io.cloudmock.core.internal.WireMockStubRegistrar;
import io.cloudmock.core.internal.store.StateStoreFactory;
import io.cloudmock.core.restapi.ModuleStatus;
import io.cloudmock.core.restapi.RequestRecord;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StateStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Entry point for the CloudMock framework.
 *
 * <p>Boots an embedded HTTP server on a random available port, redirects all AWS SDK v2
 * traffic to that port via the {@code aws.endpoint-url} system property, and invokes
 * every {@link CloudMockService} discovered on the classpath to register their stubs.
 *
 * <p>This class coordinates lifecycle and exposes the public API; the work of each concern is
 * delegated to focused collaborators in {@code io.cloudmock.core.internal}: building the server
 * ({@link WireMockServerFactory}), choosing the state store ({@link StateStoreFactory}),
 * discovering and registering modules ({@link ModuleInitializer}), translating the request
 * journal ({@link RequestHistory}), and the SDK endpoint override ({@link AwsEndpointOverride}).
 *
 * <p>Typical usage in a JUnit 6 test:
 * <pre>
 * {@literal @}BeforeAll
 * static void start() { cloudMock = new CloudMock(); cloudMock.start(); }
 *
 * {@literal @}AfterAll
 * static void stop() { cloudMock.stop(); }
 * </pre>
 *
 * <p>Or with try-with-resources:
 * <pre>
 * try (CloudMock cm = new CloudMock()) {
 *     cm.start();
 *     // run tests
 * }
 * </pre>
 */
public final class CloudMock implements AutoCloseable {

    /** Default cap on retained request-history entries; bounds memory in long-lived processes. */
    public static final int DEFAULT_MAX_REQUEST_HISTORY = 1000;

    private final CloudMockSettings settings = new CloudMockSettings(DEFAULT_MAX_REQUEST_HISTORY);

    private WireMockServer server;
    private WireMockStubRegistrar registrar;
    private FaultEngine faultEngine;
    private StateStore stateStore;
    private RequestHistory requestHistory;
    private Instant startedAt;

    /**
     * Configures a directory for persistent state storage. State written to the store will
     * survive a CloudMock restart. When not set, an in-memory store is used (state lost on stop).
     *
     * <p>Must be called before {@link #start()}.
     *
     * @param directory directory where the state file is written (a {@code cloudmock-state.log}
     *                  append-only log by default; see {@link #withPersistenceBackend})
     * @throws CloudMockAlreadyStartedException if already started
     */
    public CloudMock withStoreDirectory(Path directory) {
        requireNotStarted();
        settings.setStoreDirectory(directory);
        return this;
    }

    /**
     * Selects the persistent state backend used when a store directory is configured via
     * {@link #withStoreDirectory}. Defaults to {@link StatePersistence#APPEND_LOG}, whose write
     * cost scales with the change rather than the whole store. Has no effect on an in-memory store
     * (no directory set). Must be called before {@link #start()}.
     *
     * @throws CloudMockAlreadyStartedException if already started
     */
    public CloudMock withPersistenceBackend(StatePersistence backend) {
        requireNotStarted();
        settings.setPersistenceBackend(backend);
        return this;
    }

    /**
     * Caps the number of request-history entries retained in memory. Older entries are discarded
     * once the limit is reached, bounding memory use in a long-lived standalone process. A value
     * of {@code 0} or less retains an unbounded history. Defaults to
     * {@link #DEFAULT_MAX_REQUEST_HISTORY}. Must be called before {@link #start()}.
     *
     * @throws CloudMockAlreadyStartedException if already started
     */
    public CloudMock withMaxRequestHistory(int maxEntries) {
        requireNotStarted();
        settings.setMaxRequestHistory(maxEntries);
        return this;
    }

    /**
     * Binds the server to a specific port instead of a random available one.
     * Must be called before {@link #start()}.
     *
     * @throws CloudMockAlreadyStartedException if already started
     */
    public CloudMock withPort(int port) {
        requireNotStarted();
        settings.setPort(port);
        return this;
    }

    /**
     * Restricts {@link ServiceLoader} discovery to the given service IDs. Only discovered
     * modules whose {@link CloudMockService#serviceId()} is in {@code serviceIds} are
     * registered; all others are ignored. Modules added via {@link #withService} are always
     * registered and are not affected by this filter.
     *
     * <p>Passing {@code null} (the default) registers every discovered module. Must be called
     * before {@link #start()}.
     *
     * @throws CloudMockAlreadyStartedException if the instance is already started
     */
    public CloudMock withEnabledServices(Collection<String> serviceIds) {
        requireNotStarted();
        settings.setEnabledServiceIds(serviceIds);
        return this;
    }

    /**
     * Registers a service module explicitly, in addition to any modules discovered via
     * {@link ServiceLoader}. Must be called before {@link #start()}.
     *
     * <p>Useful in module-level tests where the test classpath structure may prevent
     * ServiceLoader from discovering the module under test automatically.
     *
     * @throws CloudMockAlreadyStartedException if already started
     */
    public CloudMock withService(CloudMockService service) {
        requireNotStarted();
        settings.addExplicitService(service);
        return this;
    }

    /**
     * Starts the embedded server, registers all discovered service stubs, and injects
     * {@code aws.endpoint-url} so the AWS SDK v2 routes traffic locally.
     *
     * @throws CloudMockAlreadyStartedException if this instance is already started
     */
    public void start() {
        requireNotStarted();
        // The store, registry, fault engine, and transformer must exist before the server, since the
        // transformer is registered as a WireMock extension at server-build time. The transformer is
        // the single response path: it runs stateful handlers and applies faults as a decoration
        // over the response, so faults are not parallel shadow stubs.
        stateStore = StateStoreFactory.create(settings.storeDirectory(), settings.persistenceBackend());
        ServiceRegistry registry = new ServiceRegistry();
        faultEngine = new FaultEngine();
        CloudMockResponseTransformer transformer =
                new CloudMockResponseTransformer(stateStore, registry, faultEngine);
        server = WireMockServerFactory.createStarted(settings, transformer);
        startedAt = Instant.now();
        AwsEndpointOverride.set(server.port());

        registrar = ModuleInitializer.initialize(server, settings, stateStore, transformer, registry);
        requestHistory = new RequestHistory(server);
    }

    /**
     * Stops the embedded server and removes the {@code aws.endpoint-url} system property.
     * Safe to call on an instance that was never started.
     */
    public void stop() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
        registrar = null;
        faultEngine = null;
        stateStore = null;
        requestHistory = null;
        startedAt = null;
        AwsEndpointOverride.clear();
    }

    /**
     * Returns the shared state store. Only valid after {@link #start()}.
     * Exposed for direct state inspection in tests and for the REST API.
     *
     * @throws CloudMockNotStartedException if not yet started
     */
    public StateStore stateStore() {
        requireStarted();
        return stateStore;
    }

    /** Returns the port the server is listening on. Only valid after {@link #start()}. */
    public int port() {
        requireStarted();
        return server.port();
    }

    /** Returns the instant the server was started. Only valid after {@link #start()}. */
    public Instant startedAt() {
        requireStarted();
        return startedAt;
    }

    /**
     * Returns a live snapshot of all loaded modules and their registered stubs.
     * Only valid after {@link #start()}.
     */
    public List<ModuleStatus> modules() {
        requireStarted();
        return registrar.moduleStatuses();
    }

    /**
     * Returns all requests served since startup, newest first.
     * Only valid after {@link #start()}.
     */
    public List<RequestRecord> requestHistory() {
        requireStarted();
        return requestHistory.all();
    }

    /**
     * Returns all requests served to the given service since startup, newest first.
     * Unmatched requests (null serviceId) are excluded.
     * Only valid after {@link #start()}.
     */
    public List<RequestRecord> requestHistory(String serviceId) {
        requireStarted();
        return requestHistory.forService(serviceId);
    }

    /**
     * Clears the captured request history, leaving registered stubs and stored state intact.
     * Only valid after {@link #start()}.
     */
    public void clearHistory() {
        requireStarted();
        requestHistory.clear();
    }

    /**
     * Causes all stubs for {@code serviceId} to return an AWS-style throttling error
     * (HTTP 400, {@code ThrottlingException}) for the duration of the current test.
     *
     * <p>Call {@link #clearFaults(String)} or {@link #clearAllFaults()} to restore normal
     * behaviour.
     *
     * @throws CloudMockNotStartedException if not yet started
     */
    public void simulateThrottle(String serviceId) {
        requireStarted();
        faultEngine.injectThrottle(serviceId);
    }

    /**
     * Causes all stubs for {@code serviceId} to respond after a long fixed delay, triggering
     * the AWS SDK's call timeout exception.
     *
     * @throws CloudMockNotStartedException if not yet started
     */
    public void simulateTimeout(String serviceId) {
        requireStarted();
        faultEngine.injectTimeout(serviceId);
    }

    /**
     * Causes approximately {@code rate} fraction of requests to {@code serviceId} to fail with
     * a connection reset; the remainder are served normally.
     *
     * <p>Use {@code rate = 0.0} or {@code rate = 1.0} for deterministic test assertions.
     * Fractional rates are statistical and unsuitable for exact-count assertions.
     *
     * @param rate fraction of requests to fault, in [0.0, 1.0]
     * @throws CloudMockNotStartedException if not yet started
     */
    public void simulateNetworkBrownout(String serviceId, double rate) {
        requireStarted();
        faultEngine.injectBrownout(serviceId, rate);
    }

    /**
     * Removes all active fault stubs for {@code serviceId}, restoring normal stub behaviour.
     *
     * @throws CloudMockNotStartedException if not yet started
     */
    public void clearFaults(String serviceId) {
        requireStarted();
        faultEngine.clearFaults(serviceId);
    }

    /**
     * Removes all active fault stubs for every service. Safe to call even when no faults
     * are active.
     *
     * <p>Called automatically by {@code CloudMockExtension} after each test method.
     */
    public void clearAllFaults() {
        if (faultEngine == null) {
            return;
        }
        faultEngine.clearAllFaults();
    }

    @Override
    public void close() {
        stop();
    }

    private void requireStarted() {
        if (server == null) {
            throw new CloudMockNotStartedException();
        }
    }

    private void requireNotStarted() {
        if (server != null) {
            throw new CloudMockAlreadyStartedException();
        }
    }
}

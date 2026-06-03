package io.cloudmock.core;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.cloudmock.core.internal.Md5HandlebarsHelper;
import io.cloudmock.core.exception.CloudMockAlreadyStartedException;
import io.cloudmock.core.exception.CloudMockNotStartedException;
import io.cloudmock.core.internal.WireMockStubRegistrar;
import io.cloudmock.core.spi.CloudMockService;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Entry point for the CloudMock framework.
 *
 * <p>Boots an embedded HTTP server on a random available port, redirects all AWS SDK v2
 * traffic to that port via the {@code aws.endpoint-url} system property, and invokes
 * every {@link CloudMockService} discovered on the classpath to register their stubs.
 *
 * <p>Typical usage in a Junit 6 test:
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

    private static final String ENDPOINT_PROPERTY = "aws.endpoint-url";

    private WireMockServer server;
    private final List<CloudMockService> explicitServices = new ArrayList<>();

    /**
     * Registers a service module explicitly, in addition to any modules discovered via
     * {@link ServiceLoader}. Must be called before {@link #start()}.
     *
     * <p>Useful in module-level tests where the test classpath structure may prevent
     * ServiceLoader from discovering the module under test automatically.
     *
     * @throws CloudMockAlreadyStartedException if the instance is already started
     */
    public CloudMock withService(CloudMockService service) {
        if (server != null) {
            throw new CloudMockAlreadyStartedException();
        }
        explicitServices.add(service);
        return this;
    }

    /**
     * Starts the embedded server, registers all discovered service stubs, and injects
     * {@code aws.endpoint-url} so the AWS SDK v2 routes traffic locally.
     *
     * @throws CloudMockAlreadyStartedException if this instance is already started
     */
    public void start() {
        if (server != null) {
            throw new CloudMockAlreadyStartedException();
        }
        server = new WireMockServer(wireMockConfig());
        server.start();
        System.setProperty(ENDPOINT_PROPERTY, "http://localhost:" + server.port());
        loadAndRegisterServices();
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
        System.clearProperty(ENDPOINT_PROPERTY);
    }

    /** Returns the port the server is listening on. Only valid after {@link #start()}. */
    public int port() {
        if (server == null) {
            throw new CloudMockNotStartedException();
        }
        return server.port();
    }

    @Override
    public void close() {
        stop();
    }

    private void loadAndRegisterServices() {
        WireMockStubRegistrar registrar = new WireMockStubRegistrar(server);
        ServiceLoader.load(CloudMockService.class, Thread.currentThread().getContextClassLoader())
                .forEach(s -> s.register(registrar));
        explicitServices.forEach(s -> s.register(registrar));
    }

    private static WireMockConfiguration wireMockConfig() {
        return WireMockConfiguration.options()
                .dynamicPort()
                .globalTemplating(true)
                .extensions(new Md5HandlebarsHelper());
    }
}

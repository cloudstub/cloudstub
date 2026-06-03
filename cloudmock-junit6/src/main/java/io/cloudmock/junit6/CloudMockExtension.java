package io.cloudmock.junit6;

import io.cloudmock.core.CloudMock;
import io.cloudmock.core.spi.CloudMockService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Junit 6 extension that manages the {@link CloudMock} lifecycle around a test class.
 *
 * <h2>Usage - zero-boilerplate ({@code @ExtendWith})</h2>
 * <pre>
 * {@literal @}ExtendWith(CloudMockExtension.class)
 * class MyTest { ... }
 * </pre>
 *
 * <h2>Usage — port access ({@code @RegisterExtension})</h2>
 * <pre>
 * {@literal @}RegisterExtension
 * static CloudMockExtension cloudMock = new CloudMockExtension()
 *         .withService(new CloudMockSqsService());
 *
 * {@literal @}Test
 * void myTest() {
 *     int port = cloudMock.port();
 * }
 * </pre>
 *
 * <p>Each test class gets an independent {@code CloudMock} instance — one class stopping
 * never affects another class's running instance.
 */
public final class CloudMockExtension implements BeforeAllCallback, AfterAllCallback {

    private final List<CloudMockService> services = new ArrayList<>();
    private CloudMock cloudMock;

    /**
     * Registers a service module to be installed when CloudMock starts.
     * Must be called before the extension starts (i.e. before any test runs).
     *
     * @return {@code this} for fluent chaining
     */
    public CloudMockExtension withService(CloudMockService service) {
        services.add(service);
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        cloudMock = new CloudMock();
        services.forEach(cloudMock::withService);
        cloudMock.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (cloudMock != null) {
            cloudMock.stop();
            cloudMock = null;
        }
    }

    /**
     * Returns the port the embedded server is listening on.
     * Only valid inside a test method (after {@code beforeAll} and before {@code afterAll}).
     */
    public int port() {
        return cloudMock.port();
    }
}

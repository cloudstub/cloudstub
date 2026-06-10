package io.cloudmock.junit;

import io.cloudmock.core.CloudMock;
import io.cloudmock.core.spi.CloudMockService;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * JUnit extension (JUnit 5 and 6) that manages the {@link CloudMock} lifecycle around a test class and
 * applies fault injection annotations ({@link SimulateThrottle}, {@link SimulateTimeout},
 * {@link SimulateNetworkBrownout}) to individual test methods.
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
 * <h2>Fault injection</h2>
 * <pre>
 * {@literal @}SimulateThrottle(service = "sqs")
 * {@literal @}Test
 * void throttledTest() { ... }
 * </pre>
 *
 * <p>Fault state is always cleaned up after each test method, even if the test throws.
 * Each test class gets an independent {@code CloudMock} instance — one class stopping
 * never affects another class's running instance.
 */
public final class CloudMockExtension
        implements BeforeAllCallback, AfterAllCallback,
                   BeforeTestExecutionCallback, AfterTestExecutionCallback {

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
    public void beforeAll(@NonNull ExtensionContext context) {
        cloudMock = new CloudMock();
        services.forEach(cloudMock::withService);
        cloudMock.start();
    }

    @Override
    public void afterAll(@NonNull ExtensionContext context) {
        if (cloudMock != null) {
            cloudMock.stop();
            cloudMock = null;
        }
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        FaultInjectionExtension.applyFaults(context.getRequiredTestMethod(), cloudMock);
    }

    @Override
    public void afterTestExecution(@NonNull ExtensionContext context) {
        cloudMock.clearAllFaults();
    }

    /**
     * Returns the port the embedded server is listening on.
     * Only valid inside a test method (after {@code beforeAll} and before {@code afterAll}).
     */
    public int port() {
        return cloudMock.port();
    }
}

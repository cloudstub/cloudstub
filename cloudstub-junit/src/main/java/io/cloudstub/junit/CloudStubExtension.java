package io.cloudstub.junit;

import io.cloudstub.core.CloudStub;
import io.cloudstub.core.spi.CloudStubService;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension (JUnit 5 and 6) that manages the {@link CloudStub} lifecycle around a test class
 * and applies fault injection annotations ({@link SimulateThrottle}, {@link SimulateTimeout},
 * {@link SimulateNetworkBrownout}) to individual test methods.
 *
 * <h2>Usage - zero-boilerplate ({@code @ExtendWith})</h2>
 *
 * <pre>
 * {@literal @}ExtendWith(CloudStubExtension.class)
 * class MyTest { ... }
 * </pre>
 *
 * <h2>Usage — port access ({@code @RegisterExtension})</h2>
 *
 * <pre>
 * {@literal @}RegisterExtension
 * static CloudStubExtension cloudMock = new CloudStubExtension()
 *         .withService(new CloudStubSqsService());
 *
 * {@literal @}Test
 * void myTest() {
 *     int port = cloudMock.port();
 * }
 * </pre>
 *
 * <h2>Fault injection</h2>
 *
 * <pre>
 * {@literal @}SimulateThrottle(service = "sqs")
 * {@literal @}Test
 * void throttledTest() { ... }
 * </pre>
 *
 * <p>Fault state is always cleaned up after each test method, even if the test throws. Each test
 * class gets an independent {@code CloudStub} instance — one class stopping never affects another
 * class's running instance.
 */
public final class CloudStubExtension
        implements BeforeAllCallback,
                AfterAllCallback,
                BeforeTestExecutionCallback,
                AfterTestExecutionCallback {

    private final List<CloudStubService> services = new ArrayList<>();
    private CloudStub cloudMock;

    /**
     * Registers a service module to be installed when CloudStub starts. Must be called before the
     * extension starts (i.e. before any test runs).
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withService(CloudStubService service) {
        services.add(service);
        return this;
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext context) {
        cloudMock = new CloudStub();
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
     * Returns the port the embedded server is listening on. Only valid inside a test method (after
     * {@code beforeAll} and before {@code afterAll}).
     */
    public int port() {
        return cloudMock.port();
    }
}

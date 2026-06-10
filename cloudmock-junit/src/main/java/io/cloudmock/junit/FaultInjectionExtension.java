package io.cloudmock.junit;

import io.cloudmock.core.CloudMock;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;

/**
 * Standalone JUnit 6 extension that applies fault injection annotations to test methods.
 *
 * <p>Use this extension when you manage the {@link CloudMock} lifecycle yourself (e.g.
 * via {@code @BeforeAll} / {@code @AfterAll}) rather than through
 * {@link CloudMockExtension}.
 *
 * <pre>
 * static CloudMock cloudMock = new CloudMock();
 *
 * {@literal @}BeforeAll
 * static void start() { cloudMock.start(); }
 *
 * {@literal @}AfterAll
 * static void stop() { cloudMock.stop(); }
 *
 * {@literal @}RegisterExtension
 * FaultInjectionExtension faults = new FaultInjectionExtension(cloudMock);
 *
 * {@literal @}SimulateThrottle(service = "sqs")
 * {@literal @}Test
 * void throttleTest() { ... }
 * </pre>
 *
 * <p>Fault state is always cleaned up after each test method, even if the test throws.
 * If you use {@link CloudMockExtension}, fault annotation support is built in — you do
 * not need this extension as well.
 */
public final class FaultInjectionExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final CloudMock cloudMock;

    public FaultInjectionExtension(CloudMock cloudMock) {
        this.cloudMock = cloudMock;
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        applyFaults(context.getRequiredTestMethod());
    }

    @Override
    public void afterTestExecution(@NonNull ExtensionContext context) {
        cloudMock.clearAllFaults();
    }

    static void applyFaults(Method method, CloudMock cloudMock) {
        for (SimulateThrottle ann : method.getAnnotationsByType(SimulateThrottle.class)) {
            cloudMock.simulateThrottle(ann.service());
        }
        for (SimulateTimeout ann : method.getAnnotationsByType(SimulateTimeout.class)) {
            cloudMock.simulateTimeout(ann.service());
        }
        for (SimulateNetworkBrownout ann : method.getAnnotationsByType(SimulateNetworkBrownout.class)) {
            cloudMock.simulateNetworkBrownout(ann.service(), ann.rate());
        }
    }

    private void applyFaults(Method method) {
        applyFaults(method, cloudMock);
    }
}

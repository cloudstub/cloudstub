package io.cloudmock.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes all stubs for the named service to return a {@code ThrottlingException} (HTTP 400)
 * for the duration of the annotated test method.
 *
 * <p>Repeatable — multiple services can be throttled simultaneously:
 * <pre>
 * {@literal @}SimulateThrottle(service = "sqs")
 * {@literal @}SimulateThrottle(service = "secretsmanager")
 * {@literal @}Test
 * void testCombinedThrottle() { ... }
 * </pre>
 *
 * <p>Fault state is always cleaned up after the test, even if it throws.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SimulateThrottle.List.class)
public @interface SimulateThrottle {

    /** Service ID to throttle, e.g. {@code "sqs"} or {@code "secretsmanager"}. */
    String service();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        SimulateThrottle[] value();
    }
}

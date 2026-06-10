package io.cloudmock.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes all stubs for the named service to delay their response beyond the AWS SDK's call
 * timeout, triggering a timeout exception at the SDK level.
 *
 * <p>Configure the SDK client under test with a short {@code apiCallTimeout} (e.g. 500 ms)
 * so the test completes quickly rather than waiting for the full 30-second server delay.
 *
 * <p>Fault state is always cleaned up after the test, even if it throws.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SimulateTimeout.List.class)
public @interface SimulateTimeout {

    /** Service ID to delay, e.g. {@code "sqs"} or {@code "secretsmanager"}. */
    String service();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        SimulateTimeout[] value();
    }
}

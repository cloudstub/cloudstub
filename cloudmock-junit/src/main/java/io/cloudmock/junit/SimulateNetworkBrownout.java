package io.cloudmock.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes approximately {@code rate} fraction of requests to the named service to fail with
 * a connection reset; the remainder are served normally.
 *
 * <p>Use {@code rate = 1.0} for "always fail" or {@code rate = 0.0} for "never fail" in
 * deterministic test assertions. Fractional rates are statistical and unsuitable for
 * exact-count assertions.
 *
 * <p>Fault state is always cleaned up after the test, even if it throws.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SimulateNetworkBrownout.List.class)
public @interface SimulateNetworkBrownout {

    /** Service ID to affect, e.g. {@code "sqs"} or {@code "secretsmanager"}. */
    String service();

    /**
     * Fraction of requests to fail, in [0.0, 1.0].
     * Defaults to {@code 0.5} (approximately half of requests fail).
     */
    double rate() default 0.5;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        SimulateNetworkBrownout[] value();
    }
}

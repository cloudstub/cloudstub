package io.cloudmock.core.internal;

/**
 * Manages the {@code aws.endpoint-url} system property that redirects AWS SDK v2 traffic to the
 * embedded mock server. Set on start, cleared on stop.
 */
public final class AwsEndpointOverride {

    private static final String ENDPOINT_PROPERTY = "aws.endpoint-url";

    private AwsEndpointOverride() {}

    /** Points the AWS SDK at {@code http://localhost:<port>}. */
    public static void set(int port) {
        System.setProperty(ENDPOINT_PROPERTY, "http://localhost:" + port);
    }

    /** Removes the override so the AWS SDK reverts to its normal endpoint resolution. */
    public static void clear() {
        System.clearProperty(ENDPOINT_PROPERTY);
    }
}

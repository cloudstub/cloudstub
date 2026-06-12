package io.cloudstub.sdkv1;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;

/**
 * Helper for pointing AWS SDK v1 clients at a running CloudStub instance.
 *
 * <p>Usage:
 *
 * <pre>
 * AmazonSQS client = AmazonSQSClientBuilder.standard()
 *     .withEndpointConfiguration(CloudStubV1Endpoints.forPort(cloudMock.port()))
 *     .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
 *     .build();
 * </pre>
 */
public final class CloudStubV1Endpoints {

    // SDK v1 requires a signing region even when talking to a local server; it has no effect on
    // stub
    // matching.
    private static final String DUMMY_REGION = "us-east-1";

    private CloudStubV1Endpoints() {}

    /**
     * Returns an {@link EndpointConfiguration} that redirects any SDK v1 client to {@code
     * http://localhost:<port>}.
     */
    public static EndpointConfiguration forPort(int port) {
        return new EndpointConfiguration("http://localhost:" + port, DUMMY_REGION);
    }
}

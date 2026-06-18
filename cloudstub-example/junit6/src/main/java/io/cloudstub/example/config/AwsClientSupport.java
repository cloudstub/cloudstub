package io.cloudstub.example.config;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/** Base for the per-service AWS client {@code @Configuration} classes. */
abstract class AwsClientSupport {

    /**
     * Builds an SDK client with anonymous credentials in {@code us-east-1}, redirected to {@code
     * endpointUrl} when it is non-empty and left on the SDK's default regional endpoint otherwise.
     */
    protected <C, B extends AwsClientBuilder<B, C>> C build(B builder, String endpointUrl) {
        builder.credentialsProvider(AnonymousCredentialsProvider.create()).region(Region.US_EAST_1);
        if (!endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }
}

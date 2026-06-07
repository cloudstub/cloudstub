package io.cloudmock.example.sdkv1;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishResult;
import io.cloudmock.junit6.CloudMockExtension;
import io.cloudmock.sdkv1.CloudMockV1Endpoints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives the {@link SnsV1PublishStubService} bring-your-own-stub example end-to-end with a real
 * AWS SDK <strong>v1</strong> SNS client, asserting a populated response (not merely connectivity)
 * — proving the user-authored XML/QUERY stub is matched and served.
 *
 * <p>Uses {@link CloudMockExtension} with {@code withService(...)} to install the user-authored stub.
 * The {@code cloudmock-sns} first-party module is not on this test classpath, so there is no other
 * {@code Publish} stub to compete with — the user-authored QUERY stub is the only {@code Publish}
 * handler.
 */
class SnsV1PublishStubExampleTest {

    @RegisterExtension
    static CloudMockExtension cloudMock =
            new CloudMockExtension().withService(new SnsV1PublishStubService());

    static AmazonSNS snsClient;

    @BeforeAll
    static void buildClient() {
        snsClient = AmazonSNSClientBuilder.standard()
                .withEndpointConfiguration(CloudMockV1Endpoints.forPort(cloudMock.port())) // (1)!
                .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())) // (2)!
                .build();
    }

    @AfterAll
    static void shutdownClient() {
        if (snsClient != null) snsClient.shutdown();
    }

    @Test
    void v1PublishIsMatchedAndReturnsPopulatedMessageId() {
        PublishResult result = snsClient.publish(
                "arn:aws:sns:us-east-1:000000000000:demo-topic", "hello from SDK v1");

        // A populated MessageId proves the XML/QUERY stub matched and the response body was parsed
        // by the v1 client — response fidelity, not just connectivity.
        assertNotNull(result.getMessageId());
        assertFalse(result.getMessageId().isBlank());
    }
}

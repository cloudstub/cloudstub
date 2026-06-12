package io.cloudstub.sdkv1;

import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.cloudstub.core.CloudStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudStubV1EndpointsTest {

    static CloudStub cloudMock;
    static AmazonSQS sqsClient;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub();
        cloudMock.start();

        sqsClient =
                AmazonSQSClientBuilder.standard()
                        .withEndpointConfiguration(CloudStubV1Endpoints.forPort(cloudMock.port()))
                        .withCredentials(
                                new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
                        .build();
    }

    @AfterAll
    static void stop() {
        if (sqsClient != null) sqsClient.shutdown();
        if (cloudMock != null) cloudMock.stop();
    }

    @Test
    void forPortReturnsEndpointWithCorrectServiceEndpoint() {
        var cfg = CloudStubV1Endpoints.forPort(9999);
        assertEquals("http://localhost:9999", cfg.getServiceEndpoint());
    }

    @Test
    void forPortReturnsEndpointWithDummySigningRegion() {
        var cfg = CloudStubV1Endpoints.forPort(9999);
        assertEquals("us-east-1", cfg.getSigningRegion());
    }

    @Test
    void requestReachesCloudStubWithoutConnectionError() {
        // SDK v1 SQS uses QUERY protocol; CloudStub has no QUERY stubs registered, so WireMock
        // returns 404. AmazonServiceException (an HTTP response) proves the connection succeeded.
        assertThrows(AmazonServiceException.class, () -> sqsClient.listQueues());
    }
}

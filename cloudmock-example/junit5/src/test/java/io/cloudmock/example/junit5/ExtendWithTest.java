package io.cloudmock.example.junit5;

import io.cloudmock.junit.CloudMockExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates {@code @ExtendWith(CloudMockExtension.class)} with JUnit 5.
 * CloudMock sets {@code aws.endpoint-url} before any test runs.
 */
@ExtendWith(CloudMockExtension.class)
class ExtendWithTest {

    static SqsClient sqsClient;

    @BeforeAll
    static void buildClient() {
        sqsClient = SqsClient.builder()
                .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }

    @AfterAll
    static void closeClient() {
        if (sqsClient != null) sqsClient.close();
    }

    @Test
    void createQueueReturnsNonBlankUrl() {
        String queueUrl = sqsClient.createQueue(b -> b.queueName("junit5-queue")).queueUrl();
        assertNotNull(queueUrl);
        assertFalse(queueUrl.isBlank());
    }

    @Test
    void sendAndReceiveRoundTrip() {
        String queueUrl = sqsClient.createQueue(b -> b.queueName("junit5-roundtrip")).queueUrl();
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody("hello from junit5"));

        var messages = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl)).messages();
        assertFalse(messages.isEmpty());
        assertEquals("hello from junit5", messages.get(0).body());
    }
}

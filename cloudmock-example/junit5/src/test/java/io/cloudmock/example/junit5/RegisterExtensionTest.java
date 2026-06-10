package io.cloudmock.example.junit5;

import io.cloudmock.junit.CloudMockExtension;
import io.cloudmock.sqs.CloudMockSqsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates {@code @RegisterExtension} with JUnit 5 — gives direct port access and
 * allows explicit service registration rather than relying on ServiceLoader.
 */
class RegisterExtensionTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension()
            .withService(new CloudMockSqsService());

    static SqsClient sqsClient;

    @BeforeAll
    static void buildClient() {
        sqsClient = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
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
        String queueUrl = sqsClient.createQueue(b -> b.queueName("junit5-reg-queue")).queueUrl();
        assertNotNull(queueUrl);
        assertFalse(queueUrl.isBlank());
    }

    @Test
    void portIsAccessibleFromTest() {
        assertTrue(cloudMock.port() > 0);
    }
}

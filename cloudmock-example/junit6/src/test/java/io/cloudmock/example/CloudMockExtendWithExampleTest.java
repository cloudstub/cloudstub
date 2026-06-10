package io.cloudmock.example;

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
 * Demonstrates {@code @ExtendWith(CloudMockExtension.class)} — the zero-boilerplate pattern.
 * CloudMock starts before the first test, stops after the last, and sets
 * {@code aws.endpoint-url} automatically.
 *
 * <p>Uses only JUnit Jupiter APIs ({@code org.junit.jupiter}), which are stable across
 * JUnit 5 and JUnit 6. {@code cloudmock-junit} declares JUnit as {@code compileOnly},
 * so the JUnit version on the classpath is determined entirely by the consumer's project.
 */
@ExtendWith(CloudMockExtension.class)
class CloudMockExtendWithExampleTest {

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
        String queueUrl = sqsClient.createQueue(b -> b.queueName("example-queue")).queueUrl();

        assertNotNull(queueUrl);
        assertFalse(queueUrl.isBlank());
    }
}

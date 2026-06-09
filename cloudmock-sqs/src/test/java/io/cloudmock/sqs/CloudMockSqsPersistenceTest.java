package io.cloudmock.sqs;

import io.cloudmock.core.CloudMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that SQS state written through the AWS SDK survives a full CloudMock restart when a
 * persistent store directory is configured — the hard requirement behind issues 0024/0035/0044.
 */
class CloudMockSqsPersistenceTest {

    @Test
    void messageSurvivesRestart(@TempDir Path storeDir) {
        String queueUrl;

        // First boot: send a message, then shut down.
        try (CloudMock cloudMock = new CloudMock()
                .withStoreDirectory(storeDir)
                .withService(new CloudMockSqsService())) {
            cloudMock.start();
            try (SqsClient sqs = client(cloudMock.port())) {
                queueUrl = sqs.createQueue(b -> b.queueName("durable")).queueUrl();
                sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody("persisted payload"));
            }
        }

        // Second boot against the same directory: the message must still be there.
        try (CloudMock cloudMock = new CloudMock()
                .withStoreDirectory(storeDir)
                .withService(new CloudMockSqsService())) {
            cloudMock.start();
            try (SqsClient sqs = client(cloudMock.port())) {
                List<Message> messages = sqs.receiveMessage(b -> b.queueUrl(queueUrl)).messages();
                assertEquals(1, messages.size(), "message must survive a restart");
                assertEquals("persisted payload", messages.get(0).body());
            }
        }
    }

    private static SqsClient client(int port) {
        return SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }
}

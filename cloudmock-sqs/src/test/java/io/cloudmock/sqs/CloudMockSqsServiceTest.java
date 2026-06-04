package io.cloudmock.sqs;

import io.cloudmock.core.CloudMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudMockSqsServiceTest {

    static CloudMock cloudMock;
    static SqsClient sqsClient;

    static final String QUEUE_NAME = "test-queue";
    static final String QUEUE_URL  = "http://localhost/000000000000/" + QUEUE_NAME;

    @BeforeAll
    static void start() {
        cloudMock = new CloudMock()
                .withService(new CloudMockSqsService());
        cloudMock.start();

        sqsClient = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }

    @AfterAll
    static void stop() {
        sqsClient.close();
        cloudMock.stop();
    }

    /** Sends a raw JSON request with X-Amz-Target — verifies stub registration and JSON matching. */
    @Test
    void rawJsonRequestMatchesStub() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .header("Content-Type", "application/x-amz-json-1.0")
                        .header("X-Amz-Target", "AmazonSQS.ListQueues")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "JSON stub did not match — check stub registration");
    }

    @Test
    void createQueueReturnsQueueUrlContainingQueueName() {
        String queueUrl = sqsClient.createQueue(b -> b.queueName(QUEUE_NAME)).queueUrl();
        assertNotNull(queueUrl);
        assertTrue(queueUrl.contains(QUEUE_NAME));
    }

    @Test
    void getQueueUrlReturnsNonNullQueueUrl() {
        String queueUrl = sqsClient.getQueueUrl(b -> b.queueName(QUEUE_NAME)).queueUrl();
        assertNotNull(queueUrl);
        assertTrue(queueUrl.contains(QUEUE_NAME));
    }

    @Test
    void sendMessageReturnsNonEmptyMessageId() {
        String messageId = sqsClient.sendMessage(b -> b
                .queueUrl(QUEUE_URL)
                .messageBody("hello from cloudmock"))
                .messageId();
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }

    @Test
    void receiveMessageReturnsMessageWithRequiredFields() {
        ReceiveMessageResponse response = sqsClient.receiveMessage(b -> b
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1));
        List<Message> messages = response.messages();
        assertFalse(messages.isEmpty());
        Message msg = messages.get(0);
        assertNotNull(msg.messageId());
        assertNotNull(msg.receiptHandle());
        assertNotNull(msg.body());
    }

    @Test
    void deleteMessageCompletesWithoutException() {
        assertDoesNotThrow(() -> sqsClient.deleteMessage(b -> b
                .queueUrl(QUEUE_URL)
                .receiptHandle("fake-receipt-handle")));
    }

    @Test
    void deleteQueueCompletesWithoutException() {
        assertDoesNotThrow(() -> sqsClient.deleteQueue(b -> b.queueUrl(QUEUE_URL)));
    }

    @Test
    void listQueuesReturnsNonNullResponse() {
        assertNotNull(sqsClient.listQueues());
    }

    @Test
    void getQueueAttributesReturnsPopulatedAttributes() {
        GetQueueAttributesResponse response = sqsClient.getQueueAttributes(b -> b
                .queueUrl(QUEUE_URL)
                .attributeNames(QueueAttributeName.ALL));
        assertNotNull(response);
        assertFalse(response.attributes().isEmpty());
    }
}

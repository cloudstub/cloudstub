package io.cloudstub.sqs;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class CloudStubSqsServiceTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline, so it is held as a shared field
    // rather than a try-with-resources local.
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static CloudStub cloudMock;
    static SqsClient sqsClient;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubSqsService());
        cloudMock.start();

        sqsClient =
                SqsClient.builder()
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

    /** Each test gets its own queue so accumulated state cannot leak between tests. */
    private String newQueue() {
        return sqsClient.createQueue(b -> b.queueName("q-" + UUID.randomUUID())).queueUrl();
    }

    /**
     * Sends a raw JSON request with X-Amz-Target — verifies stub registration and JSON matching.
     */
    @Test
    void rawJsonRequestMatchesStub() throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                .header("Content-Type", "application/x-amz-json-1.0")
                                .header("X-Amz-Target", "AmazonSQS.ListQueues")
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(
                200, response.statusCode(), "JSON stub did not match — check stub registration");
    }

    @Test
    void createQueueReturnsQueueUrlContainingQueueName() {
        String queueUrl = sqsClient.createQueue(b -> b.queueName("named-queue")).queueUrl();
        assertNotNull(queueUrl);
        assertTrue(queueUrl.contains("named-queue"));
    }

    @Test
    void getQueueUrlReturnsNonNullQueueUrl() {
        String queueUrl = sqsClient.getQueueUrl(b -> b.queueName("named-queue")).queueUrl();
        assertNotNull(queueUrl);
        assertTrue(queueUrl.contains("named-queue"));
    }

    @Test
    void sendMessageReturnsNonEmptyMessageId() {
        String queueUrl = newQueue();
        String messageId =
                sqsClient
                        .sendMessage(b -> b.queueUrl(queueUrl).messageBody("hello from cloudstub"))
                        .messageId();
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }

    @Test
    void receiveReturnsTheMessageThatWasSent() {
        String queueUrl = newQueue();
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody("round-trip payload"));

        ReceiveMessageResponse response =
                sqsClient.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(1));

        List<Message> messages = response.messages();
        assertEquals(1, messages.size());
        Message msg = messages.get(0);
        assertNotNull(msg.messageId());
        assertNotNull(msg.receiptHandle());
        assertEquals(
                "round-trip payload",
                msg.body(),
                "stateful receive must return the body that was sent");
    }

    @Test
    void messageBodyContainingHandlebarsRoundTripsVerbatim() {
        // Guards that handler output is not re-processed by WireMock's global response templating:
        // the stateful transformer fills the body after the template engine has already run.
        String queueUrl = newQueue();
        String payload = "danger {{request.url}} and {{md5 'x'}} end";
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody(payload));

        Message msg = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl)).messages().get(0);
        assertEquals(
                payload,
                msg.body(),
                "handler body must not be re-evaluated as a Handlebars template");
    }

    @Test
    void messageBodyWithJsonSpecialCharactersRoundTrips() {
        // Exercises the jackson-backed jsonField extraction + response escaping end to end.
        String queueUrl = newQueue();
        String payload = "quote \" backslash \\ newline \n tab \t unicode é end";
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody(payload));

        Message msg = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl)).messages().get(0);
        assertEquals(
                payload, msg.body(), "special characters must survive extraction and re-encoding");
    }

    @Test
    void receiveOnEmptyQueueReturnsNoMessages() {
        String queueUrl = newQueue();
        ReceiveMessageResponse response = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl));
        assertTrue(response.messages().isEmpty());
    }

    @Test
    void deleteMessageRemovesItFromTheQueue() {
        String queueUrl = newQueue();
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody("to be deleted"));

        Message msg = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl)).messages().get(0);
        sqsClient.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(msg.receiptHandle()));

        ReceiveMessageResponse after = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl));
        assertTrue(after.messages().isEmpty(), "deleted message must not be received again");
    }

    @Test
    void deleteQueueCompletesWithoutException() {
        String queueUrl = newQueue();
        assertDoesNotThrow(() -> sqsClient.deleteQueue(b -> b.queueUrl(queueUrl)));
    }

    @Test
    void listQueuesIncludesACreatedQueue() {
        sqsClient.createQueue(b -> b.queueName("listed-queue"));
        List<String> urls = sqsClient.listQueues().queueUrls();
        assertNotNull(urls);
        assertTrue(urls.stream().anyMatch(u -> u.contains("listed-queue")));
    }

    @Test
    void getQueueAttributesReflectsMessageCount() {
        String queueUrl = newQueue();
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody("one"));
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody("two"));

        GetQueueAttributesResponse response =
                sqsClient.getQueueAttributes(
                        b -> b.queueUrl(queueUrl).attributeNames(QueueAttributeName.ALL));
        assertEquals(
                "2", response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    /**
     * The operations generated from the Smithy model but not state-backed serve stateless template
     * placeholders. This verifies a representative set is registered and returns responses the AWS
     * SDK can deserialize without error (no assertion on content — these are placeholders).
     */
    @Test
    void templateBackedOperationsRespondWithoutSdkError() {
        String queueUrl = newQueue();
        assertDoesNotThrow(
                () -> {
                    sqsClient.sendMessageBatch(
                            b ->
                                    b.queueUrl(queueUrl)
                                            .entries(
                                                    SendMessageBatchRequestEntry.builder()
                                                            .id("1")
                                                            .messageBody("batched")
                                                            .build()));
                    sqsClient.changeMessageVisibility(
                            b -> b.queueUrl(queueUrl).receiptHandle("rh").visibilityTimeout(30));
                    sqsClient.setQueueAttributes(
                            b ->
                                    b.queueUrl(queueUrl)
                                            .attributes(
                                                    Map.of(
                                                            QueueAttributeName.VISIBILITY_TIMEOUT,
                                                            "60")));
                    sqsClient.tagQueue(b -> b.queueUrl(queueUrl).tags(Map.of("env", "test")));
                    sqsClient.listQueueTags(b -> b.queueUrl(queueUrl));
                    sqsClient.untagQueue(b -> b.queueUrl(queueUrl).tagKeys("env"));
                    sqsClient.purgeQueue(b -> b.queueUrl(queueUrl));
                });
    }
}

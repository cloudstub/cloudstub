package io.cloudmock.sns;

import io.cloudmock.core.CloudMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class CloudMockSNSServiceTest {

    static CloudMock cloudMock;
    static SnsClient sns;

    static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:cloudmock-topic";

    @BeforeAll
    static void start() {
        cloudMock = new CloudMock().withService(new CloudMockSNSService());
        cloudMock.start();

        sns = SnsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }

    @AfterAll
    static void stop() {
        sns.close();
        cloudMock.stop();
    }

    /**
     * Sends a raw HTTP POST with an {@code Action} form body parameter and asserts it is matched by
     * a stub registered via {@code registerXmlFormStub}. Primary validation that the XML/Form routing
     * code path works end-to-end through the core engine — the main goal of issue #0020.
     */
    @Test
    void rawXmlFormRequestMatchesStub() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "Action=ListTopics&Version=2010-03-31"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "XML/Form stub did not match — check registerXmlFormStub routing");
        assertTrue(response.body().contains("ListTopicsResponse"),
                "response should be well-formed SNS XML");
    }

    @Test
    void createTopicReturnsNonNullTopicArn() {
        CreateTopicResponse response = sns.createTopic(b -> b.name("my-topic"));
        assertNotNull(response.topicArn());
        assertFalse(response.topicArn().isBlank());
    }

    @Test
    void publishReturnsNonNullMessageId() {
        PublishResponse response = sns.publish(b -> b
                .topicArn(TOPIC_ARN)
                .message("hello from cloudmock"));
        assertNotNull(response.messageId());
        assertFalse(response.messageId().isBlank());
    }

    @Test
    void subscribeReturnsNonNullSubscriptionArn() {
        SubscribeResponse response = sns.subscribe(b -> b
                .topicArn(TOPIC_ARN)
                .protocol("sqs")
                .endpoint("arn:aws:sqs:us-east-1:000000000000:my-queue"));
        assertNotNull(response.subscriptionArn());
        assertFalse(response.subscriptionArn().isBlank());
    }

    @Test
    void listTopicsReturnsNonEmptyList() {
        assertFalse(sns.listTopics().topics().isEmpty());
    }

    @Test
    void deleteTopicCompletesWithoutException() {
        assertDoesNotThrow(() -> sns.deleteTopic(b -> b.topicArn(TOPIC_ARN)));
    }

    @Test
    void unsubscribeCompletesWithoutException() {
        assertDoesNotThrow(() -> sns.unsubscribe(b -> b.subscriptionArn(
                TOPIC_ARN + ":00000000-0000-0000-0000-000000000000")));
    }
}

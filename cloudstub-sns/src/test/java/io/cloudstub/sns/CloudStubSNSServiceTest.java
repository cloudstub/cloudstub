package io.cloudstub.sns;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.GetTopicAttributesResponse;
import software.amazon.awssdk.services.sns.model.Subscription;

class CloudStubSNSServiceTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline, so it is held as a shared field.
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static CloudStub cloudMock;
    static SnsClient sns;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubSNSService());
        cloudMock.start();

        sns =
                SnsClient.builder()
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

    /** Each test gets its own topic name so accumulated state cannot leak between tests. */
    private String newTopicArn() {
        return sns.createTopic(b -> b.name("t-" + java.util.UUID.randomUUID())).topicArn();
    }

    /**
     * Sends a raw XML/Form request with an {@code Action} form parameter — verifies the XML/Form
     * routing code path works end to end through the core engine.
     */
    @Test
    void rawXmlFormRequestMatchesStub() throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "Action=ListTopics&Version=2010-03-31"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "XML/Form stub did not match");
        assertTrue(
                response.body().contains("ListTopicsResponse"),
                "response should be well-formed SNS XML");
    }

    @Test
    void createTopicReturnsArnContainingTopicName() {
        String topicArn = sns.createTopic(b -> b.name("named-topic")).topicArn();
        assertNotNull(topicArn);
        assertTrue(topicArn.contains("named-topic"));
    }

    @Test
    void listTopicsIncludesACreatedTopic() {
        String topicArn = newTopicArn();
        assertTrue(
                sns.listTopics().topics().stream().anyMatch(t -> t.topicArn().equals(topicArn)),
                "a created topic must be returned by ListTopics");
    }

    @Test
    void deleteTopicRemovesItFromListTopics() {
        String topicArn = newTopicArn();
        sns.deleteTopic(b -> b.topicArn(topicArn));
        assertFalse(
                sns.listTopics().topics().stream().anyMatch(t -> t.topicArn().equals(topicArn)),
                "a deleted topic must not be returned by ListTopics");
    }

    @Test
    void subscribeThenListSubscriptionsByTopicReturnsTheSubscription() {
        String topicArn = newTopicArn();
        String subscriptionArn =
                sns.subscribe(
                                b ->
                                        b.topicArn(topicArn)
                                                .protocol("sqs")
                                                .endpoint("arn:aws:sqs:us-east-1:000000000000:q"))
                        .subscriptionArn();
        assertFalse(subscriptionArn.isBlank());

        List<Subscription> subs =
                sns.listSubscriptionsByTopic(b -> b.topicArn(topicArn)).subscriptions();
        assertEquals(1, subs.size());
        assertEquals(subscriptionArn, subs.get(0).subscriptionArn());
        assertEquals("sqs", subs.get(0).protocol());
        assertEquals("arn:aws:sqs:us-east-1:000000000000:q", subs.get(0).endpoint());
    }

    @Test
    void unsubscribeRemovesTheSubscription() {
        String topicArn = newTopicArn();
        String subscriptionArn =
                sns.subscribe(b -> b.topicArn(topicArn).protocol("https").endpoint("https://x"))
                        .subscriptionArn();

        sns.unsubscribe(b -> b.subscriptionArn(subscriptionArn));

        assertTrue(
                sns.listSubscriptionsByTopic(b -> b.topicArn(topicArn)).subscriptions().isEmpty(),
                "an unsubscribed subscription must not be listed");
    }

    @Test
    void getTopicAttributesReflectsSubscriptionCount() {
        String topicArn = newTopicArn();
        sns.subscribe(b -> b.topicArn(topicArn).protocol("https").endpoint("https://a"));
        sns.subscribe(b -> b.topicArn(topicArn).protocol("https").endpoint("https://b"));

        GetTopicAttributesResponse response = sns.getTopicAttributes(b -> b.topicArn(topicArn));
        assertEquals("2", response.attributes().get("SubscriptionsConfirmed"));
        assertEquals(topicArn, response.attributes().get("TopicArn"));
    }

    @Test
    void listSubscriptionsIncludesSubscriptionsAcrossTopics() {
        String topicArn = newTopicArn();
        String subscriptionArn =
                sns.subscribe(b -> b.topicArn(topicArn).protocol("https").endpoint("https://c"))
                        .subscriptionArn();
        assertTrue(
                sns.listSubscriptions().subscriptions().stream()
                        .anyMatch(s -> s.subscriptionArn().equals(subscriptionArn)),
                "ListSubscriptions must include subscriptions from all topics");
    }

    /**
     * The operations generated from the Smithy model but not state-backed serve stateless template
     * placeholders. This verifies a representative set is registered and returns responses the AWS
     * SDK can deserialize without error (no assertion on content — these are placeholders).
     */
    @Test
    void templateBackedOperationsRespondWithoutSdkError() {
        String topicArn = newTopicArn();
        assertDoesNotThrow(
                () -> {
                    sns.publish(b -> b.topicArn(topicArn).message("hello"));
                    sns.setTopicAttributes(
                            b ->
                                    b.topicArn(topicArn)
                                            .attributeName("DisplayName")
                                            .attributeValue("d"));
                    sns.listTagsForResource(b -> b.resourceArn(topicArn));
                    sns.listPlatformApplications();
                });
    }
}

package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

class LocalIntegrationTest {

    private static final int PORT = 14566;
    private static LocalProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        // Services are opt-in: declare sqs and sns so ListQueues and ListTopics are served.
        process = LocalProcess.start(PORT, "--services=sqs,sns");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void sqsListQueuesIsServedByLocalProcess() {
        try (SqsClient sqs =
                SqsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            ListQueuesResponse response = sqs.listQueues();
            assertNotNull(response);
            assertTrue(response.sdkHttpResponse().isSuccessful());
        }
    }

    @Test
    void snsTopicIsStateBackedByLocalProcess() {
        try (SnsClient sns =
                SnsClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            String topicArn = sns.createTopic(b -> b.name("local-sns-topic")).topicArn();
            assertNotNull(topicArn);

            ListTopicsResponse response = sns.listTopics();
            assertTrue(response.sdkHttpResponse().isSuccessful());
            assertTrue(
                    response.topics().stream().anyMatch(t -> t.topicArn().equals(topicArn)),
                    "topic created over the AWS protocol must be returned by the standalone server");
        }
    }
}

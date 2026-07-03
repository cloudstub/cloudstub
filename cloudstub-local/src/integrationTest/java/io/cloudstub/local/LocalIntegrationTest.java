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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

class LocalIntegrationTest {

    private static final int PORT = 14566;
    private static LocalProcess process;

    @BeforeAll
    static void startServer() throws Exception {
        // Services are opt-in: declare the services the tests below exercise.
        process = LocalProcess.start(PORT, "--services=sqs,sns,secretsmanager,dynamodb");
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

    @Test
    void secretIsStateBackedByLocalProcess() {
        try (SecretsManagerClient secrets =
                SecretsManagerClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            secrets.createSecret(b -> b.name("local-secret").secretString("local-value"));
            assertTrue(
                    secrets.getSecretValue(b -> b.secretId("local-secret"))
                            .sdkHttpResponse()
                            .isSuccessful());
            assertTrue(
                    secrets.getSecretValue(b -> b.secretId("local-secret"))
                            .secretString()
                            .equals("local-value"),
                    "secret created over the AWS protocol must be returned by the standalone"
                            + " server");
        }
    }

    @Test
    void dynamoDbItemIsStateBackedByLocalProcess() {
        try (DynamoDbClient ddb =
                DynamoDbClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + PORT))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .build()) {

            ddb.createTable(
                    b ->
                            b.tableName("local-table")
                                    .keySchema(
                                            KeySchemaElement.builder()
                                                    .attributeName("id")
                                                    .keyType(KeyType.HASH)
                                                    .build())
                                    .attributeDefinitions(
                                            AttributeDefinition.builder()
                                                    .attributeName("id")
                                                    .attributeType(ScalarAttributeType.S)
                                                    .build())
                                    .billingMode(BillingMode.PAY_PER_REQUEST));
            ddb.putItem(
                    b ->
                            b.tableName("local-table")
                                    .item(
                                            java.util.Map.of(
                                                    "id", AttributeValue.fromS("k1"),
                                                    "payload",
                                                            AttributeValue.fromS("local-value"))));

            var item =
                    ddb.getItem(
                                    b ->
                                            b.tableName("local-table")
                                                    .key(
                                                            java.util.Map.of(
                                                                    "id",
                                                                    AttributeValue.fromS("k1"))))
                            .item();
            assertTrue(
                    "local-value".equals(item.get("payload").s()),
                    "item written over the AWS protocol must be returned by the standalone server");
        }
    }
}

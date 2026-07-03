package io.cloudstub.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.CloudStub;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Verifies that DynamoDB state written through the AWS SDK survives a full CloudStub restart when a
 * persistent store directory is configured.
 */
class CloudStubDynamoDBPersistenceTest {

    @Test
    void itemSurvivesRestart(@TempDir Path storeDir) {
        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubDynamoDBService())) {
            cloudMock.start();
            try (DynamoDbClient ddb = client(cloudMock.port())) {
                ddb.createTable(
                        b ->
                                b.tableName("durable")
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
                                b.tableName("durable")
                                        .item(
                                                Map.of(
                                                        "id", AttributeValue.fromS("k1"),
                                                        "payload", AttributeValue.fromS("kept"))));
            }
        }

        try (CloudStub cloudMock =
                new CloudStub()
                        .withStoreDirectory(storeDir)
                        .withService(new CloudStubDynamoDBService())) {
            cloudMock.start();
            try (DynamoDbClient ddb = client(cloudMock.port())) {
                var item =
                        ddb.getItem(
                                        b ->
                                                b.tableName("durable")
                                                        .key(
                                                                Map.of(
                                                                        "id",
                                                                        AttributeValue.fromS(
                                                                                "k1"))))
                                .item();
                assertEquals("kept", item.get("payload").s(), "item must survive a restart");
            }
        }
    }

    private static DynamoDbClient client(int port) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
    }
}

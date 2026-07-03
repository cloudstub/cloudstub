package io.cloudstub.dynamodb;

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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Tag;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

class CloudStubDynamoDBServiceTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline, so it is held as a shared field.
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static CloudStub cloudMock;
    static DynamoDbClient ddb;

    @BeforeAll
    static void start() {
        cloudMock = new CloudStub().withService(new CloudStubDynamoDBService());
        cloudMock.start();

        ddb =
                DynamoDbClient.builder()
                        .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .build();
    }

    @AfterAll
    static void stop() {
        ddb.close();
        cloudMock.stop();
    }

    /** Creates a partition-key-only table with a unique name and returns it. */
    private String newTable() {
        String name = "t-" + UUID.randomUUID();
        ddb.createTable(
                b ->
                        b.tableName(name)
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
        return name;
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }

    @Test
    void rawJsonRequestMatchesStub() throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                .header("Content-Type", "application/x-amz-json-1.0")
                                .header("X-Amz-Target", "DynamoDB_20120810.ListTables")
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(
                200, response.statusCode(), "JSON stub did not match — check stub registration");
    }

    @Test
    void createdTableIsActiveAndDescribable() {
        String table = newTable();
        var described = ddb.describeTable(b -> b.tableName(table)).table();
        assertEquals(table, described.tableName());
        assertEquals("ACTIVE", described.tableStatusAsString());
    }

    @Test
    void putItemIsReturnedByGetItemWithAllTypes() {
        String table = newTable();
        ddb.putItem(
                b ->
                        b.tableName(table)
                                .item(
                                        Map.of(
                                                "id", s("a1"),
                                                "name", s("Widget"),
                                                "qty", AttributeValue.fromN("5"),
                                                "active", AttributeValue.fromBool(true))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("a1"))));
        assertTrue(response.hasItem() && !response.item().isEmpty(), "item must be found");
        Map<String, AttributeValue> item = response.item();
        assertEquals("Widget", item.get("name").s());
        assertEquals("5", item.get("qty").n());
        assertEquals(Boolean.TRUE, item.get("active").bool());
    }

    @Test
    void getItemOnAbsentKeyReturnsNoItem() {
        String table = newTable();
        GetItemResponse response =
                ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("missing"))));
        assertTrue(response.item().isEmpty(), "absent key must yield no item");
    }

    @Test
    void putItemOverwritesExistingItem() {
        String table = newTable();
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("k"), "v", s("first"))));
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("k"), "v", s("second"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("k"))));
        assertEquals("second", response.item().get("v").s());
    }

    @Test
    void deleteItemRemovesIt() {
        String table = newTable();
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("d1"), "v", s("x"))));
        ddb.deleteItem(b -> b.tableName(table).key(Map.of("id", s("d1"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("d1"))));
        assertTrue(response.item().isEmpty(), "deleted item must not be returned");
    }

    @Test
    void updateItemSetExpressionMutatesAttribute() {
        String table = newTable();
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("u1"), "name", s("Old"))));

        ddb.updateItem(
                b ->
                        b.tableName(table)
                                .key(Map.of("id", s("u1")))
                                .updateExpression("SET #n = :name")
                                .expressionAttributeNames(Map.of("#n", "name"))
                                .expressionAttributeValues(Map.of(":name", s("New"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("u1"))));
        assertEquals("New", response.item().get("name").s());
    }

    @Test
    void updateItemSetsAttributeWhoseNameEndsInAKeyword() {
        // "offset" ends in the substring "set"; the clause parser must not mistake it for a SET
        // keyword and write to the wrong attribute.
        String table = newTable();
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("u3"))));

        ddb.updateItem(
                b ->
                        b.tableName(table)
                                .key(Map.of("id", s("u3")))
                                .updateExpression("SET offset = :o")
                                .expressionAttributeValues(
                                        Map.of(":o", AttributeValue.fromN("7"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("u3"))));
        assertEquals("7", response.item().get("offset").n(), "offset attribute must be set");
        assertFalse(response.item().containsKey(""), "no empty-named attribute must be created");
    }

    @Test
    void updateItemCreatesItemWhenAbsent() {
        String table = newTable();
        ddb.updateItem(
                b ->
                        b.tableName(table)
                                .key(Map.of("id", s("u2")))
                                .updateExpression("SET color = :c")
                                .expressionAttributeValues(Map.of(":c", s("blue"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName(table).key(Map.of("id", s("u2"))));
        assertEquals("blue", response.item().get("color").s());
        assertEquals("u2", response.item().get("id").s(), "update must retain the key attribute");
    }

    @Test
    void queryReturnsItemsSharingThePartitionKey() {
        String table = "e-" + UUID.randomUUID();
        ddb.createTable(
                b ->
                        b.tableName(table)
                                .keySchema(
                                        KeySchemaElement.builder()
                                                .attributeName("pk")
                                                .keyType(KeyType.HASH)
                                                .build(),
                                        KeySchemaElement.builder()
                                                .attributeName("sk")
                                                .keyType(KeyType.RANGE)
                                                .build())
                                .attributeDefinitions(
                                        AttributeDefinition.builder()
                                                .attributeName("pk")
                                                .attributeType(ScalarAttributeType.S)
                                                .build(),
                                        AttributeDefinition.builder()
                                                .attributeName("sk")
                                                .attributeType(ScalarAttributeType.S)
                                                .build())
                                .billingMode(BillingMode.PAY_PER_REQUEST));

        ddb.putItem(b -> b.tableName(table).item(Map.of("pk", s("p1"), "sk", s("s1"))));
        ddb.putItem(b -> b.tableName(table).item(Map.of("pk", s("p1"), "sk", s("s2"))));
        ddb.putItem(b -> b.tableName(table).item(Map.of("pk", s("p2"), "sk", s("s1"))));

        QueryResponse response =
                ddb.query(
                        b ->
                                b.tableName(table)
                                        .keyConditionExpression("pk = :p")
                                        .expressionAttributeValues(Map.of(":p", s("p1"))));
        assertEquals(2, response.count());
        assertTrue(response.items().stream().allMatch(i -> "p1".equals(i.get("pk").s())));
    }

    @Test
    void scanReturnsAllItems() {
        String table = newTable();
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("s1"))));
        ddb.putItem(b -> b.tableName(table).item(Map.of("id", s("s2"))));

        ScanResponse response = ddb.scan(b -> b.tableName(table));
        assertEquals(2, response.count());
    }

    @Test
    void batchWriteThenBatchGetRoundTrips() {
        String table = newTable();
        ddb.batchWriteItem(
                b ->
                        b.requestItems(
                                Map.of(
                                        table,
                                        List.of(
                                                WriteRequest.builder()
                                                        .putRequest(
                                                                PutRequest.builder()
                                                                        .item(
                                                                                Map.of(
                                                                                        "id",
                                                                                        s("b1"),
                                                                                        "name",
                                                                                        s("Batch")))
                                                                        .build())
                                                        .build()))));

        var response =
                ddb.batchGetItem(
                        b ->
                                b.requestItems(
                                        Map.of(
                                                table,
                                                KeysAndAttributes.builder()
                                                        .keys(List.of(Map.of("id", s("b1"))))
                                                        .build())));
        List<Map<String, AttributeValue>> items = response.responses().get(table);
        assertEquals(1, items.size());
        assertEquals("Batch", items.get(0).get("name").s());
    }

    @Test
    void listTablesIncludesACreatedTable() {
        String table = newTable();
        assertTrue(ddb.listTables().tableNames().contains(table));
    }

    @Test
    void deleteTableRemovesItFromListTables() {
        String table = newTable();
        ddb.deleteTable(b -> b.tableName(table));
        assertFalse(ddb.listTables().tableNames().contains(table));
    }

    @Test
    void operationOnMissingTableThrowsResourceNotFound() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> ddb.getItem(b -> b.tableName("no-such-table").key(Map.of("id", s("x")))));
    }

    @Test
    void recreatingATableThrows() {
        String table = newTable();
        assertThrows(
                software.amazon.awssdk.services.dynamodb.model.ResourceInUseException.class,
                () ->
                        ddb.createTable(
                                b ->
                                        b.tableName(table)
                                                .keySchema(
                                                        KeySchemaElement.builder()
                                                                .attributeName("id")
                                                                .keyType(KeyType.HASH)
                                                                .build())
                                                .attributeDefinitions(
                                                        AttributeDefinition.builder()
                                                                .attributeName("id")
                                                                .attributeType(
                                                                        ScalarAttributeType.S)
                                                                .build())
                                                .billingMode(BillingMode.PAY_PER_REQUEST)));
    }

    /**
     * Operations registered from the model but not state-backed serve stateless template
     * placeholders. This verifies a representative set is registered and returns responses the AWS
     * SDK can deserialize without error.
     */
    @Test
    void templateBackedOperationsRespondWithoutSdkError() {
        String table = newTable();
        assertDoesNotThrow(
                () -> {
                    ddb.describeLimits();
                    ddb.describeTimeToLive(b -> b.tableName(table));
                    ddb.updateTimeToLive(
                            b ->
                                    b.tableName(table)
                                            .timeToLiveSpecification(
                                                    t -> t.enabled(false).attributeName("ttl")));
                    ddb.tagResource(
                            b ->
                                    b.resourceArn("arn:aws:dynamodb:us-east-1:0:table/" + table)
                                            .tags(Tag.builder().key("env").value("test").build()));
                    ddb.listTagsOfResource(
                            b -> b.resourceArn("arn:aws:dynamodb:us-east-1:0:table/" + table));
                });
    }
}

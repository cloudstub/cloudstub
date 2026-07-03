package io.cloudstub.example.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/** Stores and retrieves string values in a DynamoDB table, creating it on first use. */
@Service
public class ItemRepository {

    private final DynamoDbClient dynamo;
    private final String tableName = "items";
    private boolean tableReady;

    public ItemRepository(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    /** Creates the table if this is the first call, then stores {@code value} under {@code id}. */
    public void save(String id, String value) {
        ensureTable();
        dynamo.putItem(
                b ->
                        b.tableName(tableName)
                                .item(
                                        Map.of(
                                                "id", AttributeValue.fromS(id),
                                                "value", AttributeValue.fromS(value))));
    }

    /** Returns the value stored under {@code id}, or empty if there is none. */
    public Optional<String> find(String id) {
        ensureTable();
        GetItemResponse response =
                dynamo.getItem(
                        b -> b.tableName(tableName).key(Map.of("id", AttributeValue.fromS(id))));
        if (response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.item().get("value")).map(AttributeValue::s);
    }

    private void ensureTable() {
        if (tableReady) {
            return;
        }
        dynamo.createTable(
                b ->
                        b.tableName(tableName)
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
        tableReady = true;
    }
}

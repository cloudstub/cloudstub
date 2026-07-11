# Amazon DynamoDB

## Overview

The `cloudstub-dynamodb` module mocks Amazon DynamoDB. Its operation set is generated from the AWS
DynamoDB Smithy model; AWS SDK v2 drives it with the JSON / `X-Amz-Target` protocol.

The table and item operations are **state-backed**: an item written by `PutItem` is returned by a
later `GetItem`, `Query`, or `Scan`, survives a restart when a persistent store directory is
configured, and is visible through the [REST API](../rest-api.md). Items are stored keyed by a digest
of their primary-key attribute values, so a write to the same key overwrites the earlier item. The
remaining operations return well-formed but stateless placeholder responses.
See [Supported operations](#supported-operations) for the full list.

## Standalone usage

Start the standalone server with the DynamoDB module enabled (it is auto-downloaded if not already in
the plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=dynamodb
```

Applications talk to it through the **AWS SDK** by pointing the client's endpoint at the mock port
(`http://localhost:4566`), see the [Test example](#test-example) for a `DynamoDbClient` setup and
[Standalone Mode](../standalone.md) for the full configuration.

Create a table, put an item, and read it back with the AWS CLI:

```
$ aws dynamodb create-table --endpoint-url http://localhost:4566 \
    --table-name orders \
    --key-schema AttributeName=id,KeyType=HASH \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --billing-mode PAY_PER_REQUEST

$ aws dynamodb put-item --endpoint-url http://localhost:4566 \
    --table-name orders \
    --item '{"id":{"S":"o-1"},"status":{"S":"placed"}}'

$ aws dynamodb get-item --endpoint-url http://localhost:4566 \
    --table-name orders --key '{"id":{"S":"o-1"}}'
{
    "Item": {
        "id": {"S": "o-1"},
        "status": {"S": "placed"}
    }
}
```

To inspect table state from the terminal, use the [CLI](../cli.md) (`clb`), or call the
[REST API](../rest-api.md) on the API port (`4567`) directly. Parameters are passed as query-string
values. List tables and scan the items an application wrote through the SDK:

=== "CLI"

    ```
    $ clb dynamodb list-tables
    {
      "tables" : [ "orders" ]
    }

    $ clb dynamodb scan --table orders
    {
      "items" : [ {
        "id" : { "S" : "o-1" },
        "status" : { "S" : "placed" }
      } ]
    }
    ```

=== "curl"

    ```
    $ curl -s "http://localhost:4567/api/dynamodb/list-tables"
    {"tables":["orders"]}

    $ curl -s "http://localhost:4567/api/dynamodb/scan?table=orders"
    {"items":[{"id":{"S":"o-1"},"status":{"S":"placed"}}]}
    ```

The REST API and the SDK share the same state store, so an item your application puts through the SDK
is listed by `GET /api/dynamodb/scan`, and vice versa. The REST surface is read-oriented (items are
typed attribute maps that do not map cleanly to query-string parameters), so writes go through the
AWS SDK. See [REST API access](#rest-api-access) for the full route set.

## Test example

In embedded mode, add `cloudstub-dynamodb` (see [Getting Started](../getting-started.md)) and exercise
the service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class OrderTableTest {

    @Test
    void itemPutIsReadBack() {
        DynamoDbClient ddb = DynamoDbClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        ddb.createTable(b -> b.tableName("orders")
            .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
            .attributeDefinitions(AttributeDefinition.builder()
                .attributeName("id").attributeType(ScalarAttributeType.S).build())
            .billingMode(BillingMode.PAY_PER_REQUEST));

        ddb.putItem(b -> b.tableName("orders")
            .item(Map.of("id", AttributeValue.fromS("o-1"), "status", AttributeValue.fromS("placed"))));

        GetItemResponse response = ddb.getItem(b -> b.tableName("orders")
            .key(Map.of("id", AttributeValue.fromS("o-1"))));

        assertEquals("placed", response.item().get("status").s());
    }
}
```

## REST API access

The module exposes a [REST API](../rest-api.md) under `/api/dynamodb/…`. These routes read the same
state as the AWS-protocol stubs, so an item put with the SDK is listed by `GET /api/dynamodb/scan`.
Parameters are passed as query-string values (e.g. `GET /api/dynamodb/scan?table=orders`).

| Route                             | Parameters | Description                     |
| --------------------------------- | ---------- | ------------------------------- |
| `GET /api/dynamodb/list-tables`   | —          | List tables                     |
| `GET /api/dynamodb/describe-table`| `table`    | Describe a table                |
| `GET /api/dynamodb/scan`          | `table`    | List all items in a table       |

## Supported operations

State-backed operations return live data from the shared state store:

| Operation        | Behavior                                                                      |
| ---------------- | ----------------------------------------------------------------------------- |
| `CreateTable`    | Records the table and its key schema; returns the `TableDescription`          |
| `DescribeTable`  | Returns the table description, including the live `ItemCount`                 |
| `DeleteTable`    | Removes the table and its items                                               |
| `ListTables`     | Returns the names of all created tables                                       |
| `PutItem`        | Stores an item keyed by its primary key; overwrites an existing item          |
| `GetItem`        | Returns the stored item for a key, or no item                                 |
| `DeleteItem`     | Removes an item by key                                                        |
| `UpdateItem`     | Applies a `SET`/`REMOVE` update expression (or legacy `AttributeUpdates`)      |
| `Query`          | Returns items matching the partition key in `KeyConditionExpression`          |
| `Scan`           | Returns every item in the table                                               |
| `BatchWriteItem` | Applies the put and delete requests across tables                             |
| `BatchGetItem`   | Returns the stored items for the requested keys across tables                 |

The remaining operations are registered and return well-formed but stateless placeholder responses;
they do not read or mutate state: `UpdateTable`, `DescribeLimits`, `DescribeTimeToLive`,
`UpdateTimeToLive`, `DescribeContinuousBackups`, `ListTagsOfResource`, `TagResource`,
`UntagResource`.

## Limitations

- Secondary indexes (GSI/LSI) are not maintained: a `Query` or `Scan` with an `IndexName` reads the
  base table, matching on the base table's partition key only.
- `Query` matches the partition key only. Sort-key range conditions (`begins_with`, `BETWEEN`, `<`,
  `>`) in `KeyConditionExpression` are ignored and the whole partition is returned.
- `FilterExpression` on `Query` and `Scan` is not applied. `ProjectionExpression`, pagination
  (`Limit`, `ExclusiveStartKey`, `LastEvaluatedKey`), and `ScanIndexForward` are not applied.
- Conditional writes (`ConditionExpression`, `Expected`) are not evaluated: writes always succeed.
- `UpdateItem` supports `SET` and `REMOVE` with `ExpressionAttributeValues` / `ExpressionAttributeNames`,
  and legacy `AttributeUpdates` (`PUT`/`DELETE`). Arithmetic (`a = a + :n`), `list_append`, and the
  `ADD` / `DELETE` set actions are not applied.
- Transactions (`TransactWriteItems`, `TransactGetItems`), PartiQL (`ExecuteStatement`,
  `BatchExecuteStatement`, `ExecuteTransaction`), streams, global tables, backups, imports/exports,
  and time-to-live expiry are not simulated.
- Provisioned-throughput and capacity accounting are not simulated: `ConsumedCapacity` is not
  returned and requests are never throttled.

See also: [Troubleshooting](../troubleshooting.md) for common integration problems and workarounds.

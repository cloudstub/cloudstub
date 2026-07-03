package io.cloudstub.dynamodb;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.StubTemplates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CloudStub service module for Amazon DynamoDB.
 *
 * <p>The operation set is generated from the AWS DynamoDB Smithy model; AWS SDK v2 drives it with
 * the JSON/X-Amz-Target protocol, so requests carry an {@code X-Amz-Target} header (e.g. {@code
 * DynamoDB_20120810.PutItem}) and a JSON body, matched by {@link
 * StubRegistrar#registerJsonTargetStub}.
 *
 * <p>The table and item operations below are <strong>state-backed</strong>: each is a {@link
 * io.cloudstub.core.spi.StubHandler} that reads and writes the shared {@link StateStore}, so an
 * item written by {@code PutItem} is returned by a later {@code GetItem}, {@code Query}, or {@code
 * Scan}. State is keyed under the {@code dynamodb/} prefix (see {@link DynamoKeys}):
 *
 * <ul>
 *   <li>{@code dynamodb/tables/{name}} → table metadata (key schema + description)
 *   <li>{@code dynamodb/tables/{name}/items/{id}} → a stored item, where {@code id} is a digest of
 *       the item's primary-key attribute values
 * </ul>
 *
 * <p>An operation that names a table which was never created returns {@code
 * ResourceNotFoundException}; recreating an existing table returns {@code ResourceInUseException}.
 *
 * <p>The remaining operations are served from static Handlebars templates in {@code
 * src/main/resources/templates/}: they return well-formed but stateless placeholder responses (e.g.
 * {@code UpdateTable}, {@code TagResource}).
 *
 * <p>Not simulated: secondary indexes (GSI/LSI queries scan the base table's partition key only),
 * sort-key range conditions on {@code Query} (the partition is returned whole), {@code
 * FilterExpression} on {@code Query}/{@code Scan}, conditional writes ({@code
 * ConditionExpression}), atomic counters and set {@code ADD}/{@code DELETE} update actions,
 * transactions, PartiQL, streams, backups, and provisioned-throughput accounting.
 *
 * <p>Discovered via {@code ServiceLoader} from {@code
 * META-INF/services/io.cloudstub.core.spi.CloudStubService}.
 */
public class CloudStubDynamoDBService implements CloudStubService {

    private static final String SERVICE_ID = "dynamodb";
    private static final String PREFIX = "DynamoDB_20120810.";
    private static final String ERROR_NAMESPACE = "com.amazonaws.dynamodb.v20120810#";
    private static final String TABLE_ARN_PREFIX = "arn:aws:dynamodb:us-east-1:000000000000:table/";

    /**
     * Matches a single {@code <name> = :token} equality condition in a key-condition expression.
     */
    private static final Pattern EQUALITY = Pattern.compile("(#?[\\w-]+)\\s*=\\s*(:[\\w]+)");

    /**
     * Splits an update expression into its {@code SET}/{@code REMOVE}/{@code ADD}/{@code DELETE}
     * clauses.
     */
    private static final Pattern UPDATE_CLAUSE =
            Pattern.compile(
                    "(?is)\\b(SET|ADD|DELETE|REMOVE)\\b\\s+(.*?)\\s*"
                            + "(?=\\b(?:SET|ADD|DELETE|REMOVE)\\b\\s|$)");

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar r = context.registrar();

        // State-backed operations — handlers that read and write the shared StateStore.
        r.registerJsonTargetStub(PREFIX + "CreateTable", this::createTable);
        r.registerJsonTargetStub(PREFIX + "DescribeTable", this::describeTable);
        r.registerJsonTargetStub(PREFIX + "DeleteTable", this::deleteTable);
        r.registerJsonTargetStub(PREFIX + "ListTables", this::listTables);
        r.registerJsonTargetStub(PREFIX + "PutItem", this::putItem);
        r.registerJsonTargetStub(PREFIX + "GetItem", this::getItem);
        r.registerJsonTargetStub(PREFIX + "DeleteItem", this::deleteItem);
        r.registerJsonTargetStub(PREFIX + "UpdateItem", this::updateItem);
        r.registerJsonTargetStub(PREFIX + "Query", this::query);
        r.registerJsonTargetStub(PREFIX + "Scan", this::scan);
        r.registerJsonTargetStub(PREFIX + "BatchWriteItem", this::batchWriteItem);
        r.registerJsonTargetStub(PREFIX + "BatchGetItem", this::batchGetItem);

        // Template-backed operations — stateless placeholder responses.
        registerTemplate(r, "UpdateTable");
        registerTemplate(r, "DescribeLimits");
        registerTemplate(r, "DescribeTimeToLive");
        registerTemplate(r, "UpdateTimeToLive");
        registerTemplate(r, "DescribeContinuousBackups");
        registerTemplate(r, "ListTagsOfResource");
        registerTemplate(r, "TagResource");
        registerTemplate(r, "UntagResource");
    }

    private static void registerTemplate(StubRegistrar r, String operation) {
        r.registerJsonTargetStub(
                PREFIX + operation, StubTemplates.load(CloudStubDynamoDBService.class, operation));
    }

    // ---- Table operations ---------------------------------------------------

    private StubResponse createTable(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        if (name == null) {
            return validation("TableName is required.");
        }
        if (store.get(DynamoKeys.tableKey(name)) != null) {
            return error(400, "ResourceInUseException", "Table already exists: " + name);
        }

        List<Object> keySchema = asList(body.get("KeySchema"));
        String hashKey = null;
        String rangeKey = null;
        for (Object element : keySchema) {
            Map<String, Object> entry = asMap(element);
            if (entry == null) {
                continue;
            }
            String attr = str(entry.get("AttributeName"));
            if ("HASH".equals(str(entry.get("KeyType")))) {
                hashKey = attr;
            } else if ("RANGE".equals(str(entry.get("KeyType")))) {
                rangeKey = attr;
            }
        }
        if (hashKey == null) {
            return validation("KeySchema must define a HASH key.");
        }

        Map<String, Object> description =
                Json.object(
                        "TableName",
                        name,
                        "TableStatus",
                        "ACTIVE",
                        "KeySchema",
                        keySchema,
                        "AttributeDefinitions",
                        asList(body.get("AttributeDefinitions")),
                        "CreationDateTime",
                        Instant.now().getEpochSecond(),
                        "TableArn",
                        TABLE_ARN_PREFIX + name,
                        "TableId",
                        UUID.randomUUID().toString(),
                        "ItemCount",
                        0,
                        "TableSizeBytes",
                        0,
                        "ProvisionedThroughput",
                        Json.object(
                                "ReadCapacityUnits", 0,
                                "WriteCapacityUnits", 0,
                                "NumberOfDecreasesToday", 0));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("hashKey", hashKey);
        if (rangeKey != null) {
            meta.put("rangeKey", rangeKey);
        }
        meta.put("description", description);
        store.put(DynamoKeys.tableKey(name), meta);

        return StubResponse.json(Json.object("TableDescription", description));
    }

    private StubResponse describeTable(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        return StubResponse.json(Json.object("Table", describedTable(store, name, meta)));
    }

    private StubResponse deleteTable(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        Map<String, Object> description = describedTable(store, name, meta);
        description.put("TableStatus", "DELETING");
        store.delete(DynamoKeys.tableKey(name));
        store.clear(DynamoKeys.itemPrefix(name));
        return StubResponse.json(Json.object("TableDescription", description));
    }

    private StubResponse listTables(StubRequest req, StateStore store) {
        List<String> names = new ArrayList<>();
        for (String key : store.list(DynamoKeys.TABLES_PREFIX)) {
            if (DynamoKeys.isTableMarkerKey(key)) {
                names.add(key.substring(DynamoKeys.TABLES_PREFIX.length()));
            }
        }
        return StubResponse.json(Json.object("TableNames", names));
    }

    // ---- Item operations ----------------------------------------------------

    private StubResponse putItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        Map<String, Object> item = asMap(body.get("Item"));
        if (item == null) {
            return validation("Item is required.");
        }
        String id = DynamoItems.id(hashKey(meta), rangeKey(meta), item);
        if (id == null) {
            return validation("The provided item does not contain the table's key attributes.");
        }
        String itemKey = DynamoKeys.itemKey(name, id);
        Object previous = store.get(itemKey);
        store.put(itemKey, item);
        return returnValues(body, previous);
    }

    private StubResponse getItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        Map<String, Object> key = asMap(body.get("Key"));
        String id = key == null ? null : DynamoItems.id(hashKey(meta), rangeKey(meta), key);
        Object item = id == null ? null : store.get(DynamoKeys.itemKey(name, id));
        if (item == null) {
            return StubResponse.json(Map.of());
        }
        return StubResponse.json(Json.object("Item", item));
    }

    private StubResponse deleteItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        Map<String, Object> key = asMap(body.get("Key"));
        String id = key == null ? null : DynamoItems.id(hashKey(meta), rangeKey(meta), key);
        Object previous = null;
        if (id != null) {
            String itemKey = DynamoKeys.itemKey(name, id);
            previous = store.get(itemKey);
            store.delete(itemKey);
        }
        return returnValues(body, previous);
    }

    private StubResponse updateItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        Map<String, Object> key = asMap(body.get("Key"));
        String id = key == null ? null : DynamoItems.id(hashKey(meta), rangeKey(meta), key);
        if (id == null) {
            return validation("The provided key does not contain the table's key attributes.");
        }
        String itemKey = DynamoKeys.itemKey(name, id);
        Map<String, Object> existing = asMap(store.get(itemKey));
        Map<String, Object> item =
                existing != null ? new LinkedHashMap<>(existing) : new LinkedHashMap<>(key);

        applyUpdateExpression(
                item,
                str(body.get("UpdateExpression")),
                asMap(body.get("ExpressionAttributeValues")),
                asMap(body.get("ExpressionAttributeNames")));
        applyAttributeUpdates(item, asMap(body.get("AttributeUpdates")));

        store.put(itemKey, item);

        String returnValues = str(body.get("ReturnValues"));
        if ("ALL_NEW".equals(returnValues) || "UPDATED_NEW".equals(returnValues)) {
            return StubResponse.json(Json.object("Attributes", item));
        }
        return StubResponse.json(Map.of());
    }

    // ---- Read-set operations ------------------------------------------------

    private StubResponse query(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        String hashKey = hashKey(meta);
        String wanted = DynamoItems.scalar(queryHashValue(body, hashKey));

        List<Map<String, Object>> matches = new ArrayList<>();
        if (wanted != null) {
            for (Map<String, Object> item : items(store, name)) {
                if (wanted.equals(DynamoItems.scalar(item.get(hashKey)))) {
                    matches.add(item);
                }
            }
        }
        return itemSet(matches);
    }

    private StubResponse scan(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        String name = str(body.get("TableName"));
        Map<String, Object> meta = tableMeta(store, name);
        if (meta == null) {
            return notFound(name);
        }
        return itemSet(items(store, name));
    }

    // ---- Batch operations ---------------------------------------------------

    private StubResponse batchWriteItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        Map<String, Object> requestItems = asMap(body.get("RequestItems"));
        if (requestItems == null) {
            return validation("RequestItems is required.");
        }
        StubResponse missing = firstMissingTable(store, requestItems.keySet());
        if (missing != null) {
            return missing;
        }
        for (Map.Entry<String, Object> table : requestItems.entrySet()) {
            String name = table.getKey();
            Map<String, Object> meta = tableMeta(store, name);
            for (Object request : asList(table.getValue())) {
                Map<String, Object> write = asMap(request);
                if (write == null) {
                    continue;
                }
                Map<String, Object> put = asMap(write.get("PutRequest"));
                Map<String, Object> delete = asMap(write.get("DeleteRequest"));
                if (put != null) {
                    Map<String, Object> item = asMap(put.get("Item"));
                    String id =
                            item == null
                                    ? null
                                    : DynamoItems.id(hashKey(meta), rangeKey(meta), item);
                    if (id != null) {
                        store.put(DynamoKeys.itemKey(name, id), item);
                    }
                } else if (delete != null) {
                    Map<String, Object> key = asMap(delete.get("Key"));
                    String id =
                            key == null ? null : DynamoItems.id(hashKey(meta), rangeKey(meta), key);
                    if (id != null) {
                        store.delete(DynamoKeys.itemKey(name, id));
                    }
                }
            }
        }
        return StubResponse.json(Json.object("UnprocessedItems", Map.of()));
    }

    private StubResponse batchGetItem(StubRequest req, StateStore store) {
        Map<String, Object> body = DynamoJson.parseObject(req.body());
        Map<String, Object> requestItems = asMap(body.get("RequestItems"));
        if (requestItems == null) {
            return validation("RequestItems is required.");
        }
        StubResponse missing = firstMissingTable(store, requestItems.keySet());
        if (missing != null) {
            return missing;
        }
        Map<String, Object> responses = new LinkedHashMap<>();
        for (Map.Entry<String, Object> table : requestItems.entrySet()) {
            String name = table.getKey();
            Map<String, Object> meta = tableMeta(store, name);
            Map<String, Object> spec = asMap(table.getValue());
            List<Map<String, Object>> found = new ArrayList<>();
            for (Object rawKey : asList(spec == null ? null : spec.get("Keys"))) {
                Map<String, Object> key = asMap(rawKey);
                String id = key == null ? null : DynamoItems.id(hashKey(meta), rangeKey(meta), key);
                Map<String, Object> item =
                        id == null ? null : asMap(store.get(DynamoKeys.itemKey(name, id)));
                if (item != null) {
                    found.add(item);
                }
            }
            responses.put(name, found);
        }
        return StubResponse.json(Json.object("Responses", responses, "UnprocessedKeys", Map.of()));
    }

    // ---- Expression handling ------------------------------------------------

    private void applyUpdateExpression(
            Map<String, Object> item,
            String expression,
            Map<String, Object> values,
            Map<String, Object> names) {
        if (expression == null) {
            return;
        }
        Matcher clause = UPDATE_CLAUSE.matcher(expression);
        while (clause.find()) {
            String keyword = clause.group(1).toUpperCase();
            String operands = clause.group(2).trim();
            if ("SET".equals(keyword)) {
                for (String assignment : operands.split(",")) {
                    int equals = assignment.indexOf('=');
                    if (equals < 0) {
                        continue;
                    }
                    String name = resolveName(assignment.substring(0, equals).trim(), names);
                    Object value = resolveValue(assignment.substring(equals + 1).trim(), values);
                    if (name != null && value != null) {
                        item.put(name, value);
                    }
                }
            } else if ("REMOVE".equals(keyword)) {
                for (String path : operands.split(",")) {
                    String name = resolveName(path.trim(), names);
                    if (name != null) {
                        item.remove(name);
                    }
                }
            }
            // ADD and DELETE (set/counter actions) are not simulated.
        }
    }

    private void applyAttributeUpdates(Map<String, Object> item, Map<String, Object> updates) {
        if (updates == null) {
            return;
        }
        for (Map.Entry<String, Object> update : updates.entrySet()) {
            Map<String, Object> spec = asMap(update.getValue());
            if (spec == null) {
                continue;
            }
            String action = str(spec.get("Action"));
            if (action == null || "PUT".equals(action)) {
                Object value = spec.get("Value");
                if (value != null) {
                    item.put(update.getKey(), value);
                }
            } else if ("DELETE".equals(action) && spec.get("Value") == null) {
                item.remove(update.getKey());
            }
            // ADD is not simulated.
        }
    }

    private Object queryHashValue(Map<String, Object> body, String hashKey) {
        String expression = str(body.get("KeyConditionExpression"));
        Map<String, Object> values = asMap(body.get("ExpressionAttributeValues"));
        Map<String, Object> names = asMap(body.get("ExpressionAttributeNames"));
        if (expression != null && values != null) {
            for (String condition : expression.split("(?i)\\bAND\\b")) {
                Matcher equality = EQUALITY.matcher(condition);
                if (equality.find() && hashKey.equals(resolveName(equality.group(1), names))) {
                    return values.get(equality.group(2));
                }
            }
        }
        Map<String, Object> conditions = asMap(body.get("KeyConditions"));
        if (conditions != null) {
            Map<String, Object> condition = asMap(conditions.get(hashKey));
            if (condition != null && "EQ".equals(str(condition.get("ComparisonOperator")))) {
                List<Object> list = asList(condition.get("AttributeValueList"));
                if (!list.isEmpty()) {
                    return list.get(0);
                }
            }
        }
        return null;
    }

    private static String resolveName(String token, Map<String, Object> names) {
        if (token.startsWith("#")) {
            return names == null ? null : str(names.get(token));
        }
        return token;
    }

    private static Object resolveValue(String token, Map<String, Object> values) {
        if (token.startsWith(":")) {
            return values == null ? null : values.get(token);
        }
        return null;
    }

    // ---- Response + state helpers -------------------------------------------

    private StubResponse itemSet(List<Map<String, Object>> items) {
        return StubResponse.json(
                Json.object("Items", items, "Count", items.size(), "ScannedCount", items.size()));
    }

    private StubResponse returnValues(Map<String, Object> body, Object previous) {
        if ("ALL_OLD".equals(str(body.get("ReturnValues"))) && previous != null) {
            return StubResponse.json(Json.object("Attributes", previous));
        }
        return StubResponse.json(Map.of());
    }

    private List<Map<String, Object>> items(StateStore store, String name) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : store.list(DynamoKeys.itemPrefix(name))) {
            Map<String, Object> item = asMap(store.get(key));
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private Map<String, Object> describedTable(
            StateStore store, String name, Map<String, Object> meta) {
        Map<String, Object> description = new LinkedHashMap<>(asMap(meta.get("description")));
        description.put("ItemCount", store.list(DynamoKeys.itemPrefix(name)).size());
        return description;
    }

    private static Map<String, Object> tableMeta(StateStore store, String name) {
        return name == null ? null : asMap(store.get(DynamoKeys.tableKey(name)));
    }

    /**
     * Returns a {@code ResourceNotFoundException} response for the first requested table that does
     * not exist, or {@code null} if all exist. Used to reject a batch before applying any write, so
     * a missing table does not leave earlier tables partially mutated.
     */
    private StubResponse firstMissingTable(StateStore store, Iterable<String> tableNames) {
        for (String name : tableNames) {
            if (tableMeta(store, name) == null) {
                return notFound(name);
            }
        }
        return null;
    }

    private static String hashKey(Map<String, Object> meta) {
        return str(meta.get("hashKey"));
    }

    private static String rangeKey(Map<String, Object> meta) {
        return str(meta.get("rangeKey"));
    }

    private StubResponse notFound(String name) {
        return error(
                400, "ResourceNotFoundException", "Requested resource not found: Table: " + name);
    }

    private StubResponse validation(String message) {
        return error(400, "ValidationException", message);
    }

    private StubResponse error(int status, String type, String message) {
        return StubResponse.json(
                        status, Json.object("__type", ERROR_NAMESPACE + type, "message", message))
                .withHeader("x-amzn-ErrorType", type);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }
}

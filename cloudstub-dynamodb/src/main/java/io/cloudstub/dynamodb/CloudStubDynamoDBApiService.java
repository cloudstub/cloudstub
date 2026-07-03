package io.cloudstub.dynamodb;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API surface for DynamoDB, mounted under {@code /api/dynamodb/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clb dynamodb <command>} dynamically with no compile-time knowledge of
 * DynamoDB.
 *
 * <p>Routes are <em>state-backed</em>: they read the same {@link StateStore} (under the same {@link
 * DynamoKeys} scheme) as the AWS-protocol stubs in {@link CloudStubDynamoDBService}, so an item put
 * through the AWS SDK is listed by {@code GET /api/dynamodb/scan} and shown in the console. The
 * surface is read-oriented — items are typed attribute maps that do not map cleanly to query-string
 * parameters — so writes go through the AWS SDK.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public class CloudStubDynamoDBApiService implements CloudStubApiService {

    private static final ApiParam TABLE = new ApiParam("table", true, "Table name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "dynamodb";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(
                HttpMethod.GET,
                "/list-tables",
                "list-tables",
                "List DynamoDB tables",
                List.of(),
                this::listTables);
        r.register(
                HttpMethod.GET,
                "/describe-table",
                "describe-table",
                "Describe a DynamoDB table",
                List.of(TABLE),
                this::describeTable);
        r.register(
                HttpMethod.GET,
                "/scan",
                "scan",
                "List all items in a DynamoDB table",
                List.of(TABLE),
                this::scan);
    }

    private ApiResponse listTables(ApiRequest req) {
        List<String> tables = new ArrayList<>();
        for (String key : store.list(DynamoKeys.TABLES_PREFIX)) {
            if (DynamoKeys.isTableMarkerKey(key)) {
                tables.add(key.substring(DynamoKeys.TABLES_PREFIX.length()));
            }
        }
        return new ApiResponse(200, Map.of("tables", tables));
    }

    private ApiResponse describeTable(ApiRequest req) {
        String table = req.queryParams().getOrDefault("table", "");
        Object meta = store.get(DynamoKeys.tableKey(table));
        if (!(meta instanceof Map<?, ?> metaMap)) {
            return new ApiResponse(404, Map.of("error", "table not found: " + table));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        Object description = metaMap.get("description");
        if (description instanceof Map<?, ?> descriptionMap) {
            body.putAll(asStringKeyed(descriptionMap));
        }
        body.put("ItemCount", store.list(DynamoKeys.itemPrefix(table)).size());
        return new ApiResponse(200, body);
    }

    private ApiResponse scan(ApiRequest req) {
        String table = req.queryParams().getOrDefault("table", "");
        List<Object> items = new ArrayList<>();
        for (String key : store.list(DynamoKeys.itemPrefix(table))) {
            Object item = store.get(key);
            if (item != null) {
                items.add(item);
            }
        }
        return new ApiResponse(200, Map.of("items", items));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyed(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}

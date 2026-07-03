package io.cloudstub.dynamodb;

/**
 * The DynamoDB state-store key scheme, defined once so the AWS-protocol surface ({@link
 * CloudStubDynamoDBService}) and the REST/CLI surface ({@link CloudStubDynamoDBApiService}) address
 * exactly the same data and cannot drift. Keys live under the {@code dynamodb/} prefix:
 *
 * <ul>
 *   <li>{@code dynamodb/tables/{name}} → the table metadata (key schema + description)
 *   <li>{@code dynamodb/tables/{name}/items/{id}} → a stored item (its attribute map)
 * </ul>
 *
 * <p>The item {@code id} is a digest of the item's primary-key attribute values, so a later write
 * to the same key overwrites the earlier item, matching DynamoDB's put semantics.
 */
final class DynamoKeys {

    private DynamoKeys() {}

    static final String TABLES_PREFIX = "dynamodb/tables/";

    static String tableKey(String name) {
        return TABLES_PREFIX + name;
    }

    static String itemPrefix(String name) {
        return TABLES_PREFIX + name + "/items/";
    }

    static String itemKey(String name, String id) {
        return itemPrefix(name) + id;
    }

    /**
     * A table marker key (e.g. {@code dynamodb/tables/orders}) has no further path segment; item
     * keys do.
     */
    static boolean isTableMarkerKey(String key) {
        return key.indexOf('/', TABLES_PREFIX.length()) < 0;
    }
}

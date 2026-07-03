package io.cloudstub.dynamodb;

import io.cloudstub.core.spi.Digest;
import java.util.Map;

/**
 * Helpers for turning a DynamoDB item's primary key into a stable state-store id, and for reading
 * scalar attribute values. Dependency-free (JDK + the core SPI {@link Digest}).
 *
 * <p>An attribute value is the single-entry map DynamoDB wraps each value in, e.g. {@code {"S":
 * "abc"}} or {@code {"N": "42"}}. A primary key is the partition-key attribute plus an optional
 * sort-key attribute; the storage id is a digest of their type-tagged scalar values, so an item and
 * a {@code Key} that name the same primary key resolve to the same id.
 */
final class DynamoItems {

    private DynamoItems() {}

    /**
     * The type-tagged scalar of an attribute value, e.g. {@code "S abc"} for {@code {"S": "abc"}}.
     * Returns {@code null} if the value is not a single-entry attribute-value map. The type tag
     * keeps a string {@code "1"} and a number {@code 1} distinct.
     */
    static String scalar(Object attributeValue) {
        if (!(attributeValue instanceof Map<?, ?> map) || map.size() != 1) {
            return null;
        }
        Map.Entry<?, ?> entry = map.entrySet().iterator().next();
        return entry.getKey() + " " + String.valueOf(entry.getValue());
    }

    /**
     * The storage id for the item or key, computed from its partition-key value and, if the table
     * has one, its sort-key value. Returns {@code null} if a required key attribute is absent.
     *
     * @param hashKey the partition-key attribute name
     * @param rangeKey the sort-key attribute name, or {@code null} if the table has none
     * @param itemOrKey an item ({@code PutItem}) or a bare key ({@code GetItem}/{@code DeleteItem})
     */
    static String id(String hashKey, String rangeKey, Map<String, Object> itemOrKey) {
        String hashScalar = scalar(itemOrKey.get(hashKey));
        if (hashScalar == null) {
            return null;
        }
        StringBuilder composite = new StringBuilder(hashScalar);
        if (rangeKey != null) {
            String rangeScalar = scalar(itemOrKey.get(rangeKey));
            if (rangeScalar == null) {
                return null;
            }
            composite.append('\n').append(rangeScalar);
        }
        return Digest.md5Hex(composite.toString());
    }
}

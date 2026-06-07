package io.cloudmock.sqs;

/**
 * The SQS state-store key scheme, defined once so the AWS-protocol surface ({@link CloudMockSqsService})
 * and the REST/CLI surface ({@link CloudMockSqsApiService}) address exactly the same data and cannot
 * drift. Keys live under the {@code sqs/} prefix:
 * <ul>
 *   <li>{@code sqs/queues/{name}} → the queue URL (marks the queue's existence)</li>
 *   <li>{@code sqs/queues/{name}/messages/{id}} → the message body</li>
 * </ul>
 */
final class SqsKeys {

    private SqsKeys() {}

    static final String QUEUES_PREFIX = "sqs/queues/";

    static String queueKey(String name) {
        return QUEUES_PREFIX + name;
    }

    static String messagePrefix(String name) {
        return QUEUES_PREFIX + name + "/messages/";
    }

    static String messageKey(String name, String id) {
        return messagePrefix(name) + id;
    }

    /** A queue marker key (e.g. {@code sqs/queues/demo}) has no further path segment; messages do. */
    static boolean isQueueMarkerKey(String key) {
        return key.indexOf('/', QUEUES_PREFIX.length()) < 0;
    }
}

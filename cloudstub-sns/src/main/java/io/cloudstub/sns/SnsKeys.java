package io.cloudstub.sns;

/**
 * The SNS state-store key scheme. Keys live under the {@code sns/} prefix:
 *
 * <ul>
 *   <li>{@code sns/topics/{name}} → the topic ARN
 *   <li>{@code sns/topics/{name}/subscriptions/{id}} → the subscription record (a map of {@code
 *       subscriptionArn}, {@code protocol}, {@code endpoint})
 * </ul>
 *
 * <p>SNS topic names never contain {@code '/'}, so a topic key has no further path segment while a
 * subscription key does — see {@link #isTopicMarkerKey}.
 */
final class SnsKeys {

    private SnsKeys() {}

    static final String REGION = "us-east-1";
    static final String ACCOUNT = "000000000000";
    static final String ARN_PREFIX = "arn:aws:sns:" + REGION + ":" + ACCOUNT + ":";

    static final String TOPICS_PREFIX = "sns/topics/";

    static String topicKey(String name) {
        return TOPICS_PREFIX + name;
    }

    static String subscriptionPrefix(String name) {
        return TOPICS_PREFIX + name + "/subscriptions/";
    }

    static String subscriptionKey(String name, String id) {
        return subscriptionPrefix(name) + id;
    }

    /** The ARN for a topic of the given name. */
    static String topicArn(String name) {
        return ARN_PREFIX + name;
    }

    /** The topic name in an ARN, i.e. the segment after the last {@code ':'}. */
    static String topicNameFromArn(String topicArn) {
        return topicArn.substring(topicArn.lastIndexOf(':') + 1);
    }

    /**
     * A topic marker key (e.g. {@code sns/topics/orders}) has no further path segment;
     * subscriptions do.
     */
    static boolean isTopicMarkerKey(String key) {
        return key.indexOf('/', TOPICS_PREFIX.length()) < 0;
    }
}

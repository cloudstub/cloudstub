package io.cloudstub.sqs;

/** SQS path helpers: queue-name extraction from a queue URL. Dependency-free (JDK only). */
final class SqsHelpers {

    private SqsHelpers() {}

    /** Extracts the queue name (last path segment) from an SQS queue URL. */
    static String queueName(String queueUrl) {
        if (queueUrl == null) {
            return null;
        }
        int slash = queueUrl.lastIndexOf('/');
        return slash >= 0 ? queueUrl.substring(slash + 1) : queueUrl;
    }
}

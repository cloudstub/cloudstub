package io.cloudmock.sqs;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StateStore;
import io.cloudmock.core.spi.StubRegistrar;
import io.cloudmock.core.spi.StubRequest;
import io.cloudmock.core.spi.StubResponse;

import java.util.List;
import java.util.UUID;

/**
 * CloudMock service module for Amazon SQS — the reference <em>stateful</em> module.
 *
 * <p>AWS SDK v2 (≥2.20) uses the JSON/X-Amz-Target protocol for SQS: requests carry an
 * {@code X-Amz-Target} header (e.g. {@code AmazonSQS.SendMessage}) and a JSON body. Each operation
 * is registered as a {@link io.cloudmock.core.spi.StubHandler} that reads and writes the shared
 * {@link StateStore}, so a message sent in one call is returned by a later {@code ReceiveMessage} —
 * the module, not a template, is the bridge between the AWS protocol and the store.
 *
 * <p>State is keyed under the {@code sqs/} prefix:
 * <ul>
 *   <li>{@code sqs/queues/{name}} → the queue URL (marks the queue's existence)</li>
 *   <li>{@code sqs/queues/{name}/messages/{id}} → the message body</li>
 * </ul>
 *
 * <p>Not simulated (consistent with CloudMock's documented out-of-scope list): visibility timeout,
 * FIFO deduplication/ordering. A received message stays visible until explicitly deleted.
 *
 * <p>Discovered automatically via {@code ServiceLoader} from
 * {@code META-INF/services/io.cloudmock.core.spi.CloudMockService}.
 */
public class CloudMockSqsService implements CloudMockService {

    private static final String SERVICE_ID  = "sqs";
    private static final String PREFIX      = "AmazonSQS.";
    private static final String ACCOUNT_URL = "http://localhost/000000000000/";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudMockContext context) {
        StubRegistrar r = context.registrar();
        r.registerJsonTargetStub(PREFIX + "CreateQueue",        this::createQueue);
        r.registerJsonTargetStub(PREFIX + "GetQueueUrl",        this::getQueueUrl);
        r.registerJsonTargetStub(PREFIX + "SendMessage",        this::sendMessage);
        r.registerJsonTargetStub(PREFIX + "ReceiveMessage",     this::receiveMessage);
        r.registerJsonTargetStub(PREFIX + "DeleteMessage",      this::deleteMessage);
        r.registerJsonTargetStub(PREFIX + "DeleteQueue",        this::deleteQueue);
        r.registerJsonTargetStub(PREFIX + "ListQueues",         this::listQueues);
        r.registerJsonTargetStub(PREFIX + "GetQueueAttributes", this::getQueueAttributes);
    }

    private StubResponse createQueue(StubRequest req, StateStore store) {
        String name = req.jsonField("QueueName");
        String url = ACCOUNT_URL + name;
        store.put(queueKey(name), url);
        return StubResponse.json("{\"QueueUrl\":\"" + SqsJson.escape(url) + "\"}");
    }

    private StubResponse getQueueUrl(StubRequest req, StateStore store) {
        String name = req.jsonField("QueueName");
        return StubResponse.json("{\"QueueUrl\":\"" + SqsJson.escape(ACCOUNT_URL + name) + "\"}");
    }

    private StubResponse sendMessage(StubRequest req, StateStore store) {
        String name = SqsJson.queueName(req.jsonField("QueueUrl"));
        String body = req.jsonField("MessageBody");
        if (body == null) {
            body = "";
        }
        String id = UUID.randomUUID().toString();
        store.put(messageKey(name, id), body);
        return StubResponse.json(
                "{\"MessageId\":\"" + id + "\",\"MD5OfMessageBody\":\"" + SqsJson.md5(body) + "\"}");
    }

    private StubResponse receiveMessage(StubRequest req, StateStore store) {
        String name = SqsJson.queueName(req.jsonField("QueueUrl"));
        int max = maxMessages(req.jsonField("MaxNumberOfMessages"));
        List<String> keys = store.list(messagePrefix(name));

        StringBuilder messages = new StringBuilder();
        int count = 0;
        for (String key : keys) {
            if (count >= max) {
                break;
            }
            Object stored = store.get(key);
            if (stored == null) {
                // Concurrently deleted between list() and get(); skip rather than emit Body="null".
                continue;
            }
            String id = key.substring(key.lastIndexOf('/') + 1);
            String body = stored.toString();
            if (count > 0) {
                messages.append(',');
            }
            messages.append("{\"MessageId\":\"").append(id)
                    .append("\",\"ReceiptHandle\":\"").append(id)
                    .append("\",\"Body\":\"").append(SqsJson.escape(body))
                    .append("\",\"MD5OfBody\":\"").append(SqsJson.md5(body)).append("\"}");
            count++;
        }
        if (count == 0) {
            return StubResponse.json("{}");
        }
        return StubResponse.json("{\"Messages\":[" + messages + "]}");
    }

    private StubResponse deleteMessage(StubRequest req, StateStore store) {
        String name = SqsJson.queueName(req.jsonField("QueueUrl"));
        String receiptHandle = req.jsonField("ReceiptHandle");
        if (name != null && receiptHandle != null) {
            store.delete(messageKey(name, receiptHandle));
        }
        return StubResponse.json("{}");
    }

    private StubResponse deleteQueue(StubRequest req, StateStore store) {
        String name = SqsJson.queueName(req.jsonField("QueueUrl"));
        store.delete(queueKey(name));
        store.clear(messagePrefix(name));
        return StubResponse.json("{}");
    }

    private StubResponse listQueues(StubRequest req, StateStore store) {
        StringBuilder urls = new StringBuilder();
        int count = 0;
        for (String key : store.list(QUEUES_PREFIX)) {
            if (!isQueueMarkerKey(key)) {
                continue;
            }
            Object url = store.get(key);
            if (url == null) {
                continue;
            }
            if (count > 0) {
                urls.append(',');
            }
            urls.append('"').append(SqsJson.escape(url.toString())).append('"');
            count++;
        }
        return StubResponse.json("{\"QueueUrls\":[" + urls + "]}");
    }

    private StubResponse getQueueAttributes(StubRequest req, StateStore store) {
        String name = SqsJson.queueName(req.jsonField("QueueUrl"));
        int messageCount = store.list(messagePrefix(name)).size();
        return StubResponse.json("{\"Attributes\":{"
                + "\"VisibilityTimeout\":\"30\","
                + "\"ApproximateNumberOfMessages\":\"" + messageCount + "\","
                + "\"ApproximateNumberOfMessagesNotVisible\":\"0\","
                + "\"MaximumMessageSize\":\"262144\","
                + "\"MessageRetentionPeriod\":\"345600\","
                + "\"ReceiveMessageWaitTimeSeconds\":\"0\"}}");
    }

    private static final String QUEUES_PREFIX = "sqs/queues/";

    private static String queueKey(String name) {
        return QUEUES_PREFIX + name;
    }

    private static String messagePrefix(String name) {
        return QUEUES_PREFIX + name + "/messages/";
    }

    private static String messageKey(String name, String id) {
        return messagePrefix(name) + id;
    }

    /** A queue marker key (e.g. {@code sqs/queues/demo}) has no further path segment; messages do. */
    private static boolean isQueueMarkerKey(String key) {
        return key.indexOf('/', QUEUES_PREFIX.length()) < 0;
    }

    /** Parses a {@code MaxNumberOfMessages} value, defaulting to 1 (the AWS default) and clamping. */
    private static int maxMessages(String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            long value = Long.parseLong(raw);
            return value < 1 ? 1 : (int) Math.min(value, Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

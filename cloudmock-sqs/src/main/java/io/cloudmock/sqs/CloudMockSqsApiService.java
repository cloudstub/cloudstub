package io.cloudmock.sqs;

import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StateStore;
import io.cloudmock.core.spi.restapi.ApiParam;
import io.cloudmock.core.spi.restapi.ApiRequest;
import io.cloudmock.core.spi.restapi.ApiResponse;
import io.cloudmock.core.spi.restapi.CloudMockApiContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API surface for SQS, mounted under {@code /api/sqs/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clm sqs <command>} dynamically with no compile-time knowledge of SQS.
 *
 * <p>Routes are <em>state-backed</em>: they read and write the same {@link StateStore} (under the
 * same {@link SqsKeys} scheme) as the AWS-protocol stubs in {@link CloudMockSqsService}. So a message
 * sent through the AWS SDK is returned by {@code GET /api/sqs/receive-message} and shown in the
 * console, and vice versa — one state, two representations (AWS wire protocol vs. this friendly JSON).
 *
 * <p>Discovered via {@code META-INF/services/io.cloudmock.core.spi.CloudMockApiService}.
 */
public class CloudMockSqsApiService implements CloudMockApiService {

    private static final ApiParam QUEUE = new ApiParam("queue", true, "Queue name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "sqs";
    }

    @Override
    public void registerRoutes(CloudMockApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(HttpMethod.GET, "/list-queues", "list-queues",
                "List SQS queues", List.of(), this::listQueues);
        r.register(HttpMethod.POST, "/send-message", "send-message",
                "Send a message to an SQS queue",
                List.of(QUEUE, new ApiParam("body", true, "Message body")), this::sendMessage);
        r.register(HttpMethod.GET, "/receive-message", "receive-message",
                "Receive messages from an SQS queue", List.of(QUEUE), this::receiveMessage);
        r.register(HttpMethod.POST, "/purge-queue", "purge-queue",
                "Purge all messages from an SQS queue", List.of(QUEUE), this::purgeQueue);
    }

    private ApiResponse listQueues(ApiRequest req) {
        List<String> queues = new ArrayList<>();
        for (String key : store.list(SqsKeys.QUEUES_PREFIX)) {
            if (!SqsKeys.isQueueMarkerKey(key)) {
                continue;
            }
            Object url = store.get(key);
            if (url != null) {
                queues.add(url.toString());
            }
        }
        return new ApiResponse(200, Map.of("queues", queues));
    }

    private ApiResponse sendMessage(ApiRequest req) {
        String queue = req.queryParams().getOrDefault("queue", "");
        String body = req.queryParams().getOrDefault("body", "");
        String id = UUID.randomUUID().toString();
        store.put(SqsKeys.messageKey(queue, id), body);
        return new ApiResponse(200, Map.of(
                "messageId", id,
                "md5OfBody", SqsJson.md5(body)));
    }

    private ApiResponse receiveMessage(ApiRequest req) {
        String queue = req.queryParams().getOrDefault("queue", "");
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String key : store.list(SqsKeys.messagePrefix(queue))) {
            Object body = store.get(key);
            if (body == null) {
                continue;
            }
            String id = key.substring(key.lastIndexOf('/') + 1);
            messages.add(Map.of(
                    "messageId", id,
                    "receiptHandle", id,
                    "body", body.toString()));
        }
        return new ApiResponse(200, Map.of("messages", messages));
    }

    private ApiResponse purgeQueue(ApiRequest req) {
        String queue = req.queryParams().getOrDefault("queue", "");
        store.clear(SqsKeys.messagePrefix(queue));
        return new ApiResponse(200, Map.of(
                "status", "purged",
                "queue", queue));
    }
}

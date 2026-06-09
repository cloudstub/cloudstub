package io.cloudmock.sqs;

import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.restapi.ApiParam;
import io.cloudmock.core.spi.restapi.ApiRequest;
import io.cloudmock.core.spi.restapi.ApiResponse;
import io.cloudmock.core.spi.restapi.ApiRouteRegistrar;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API surface for SQS, mounted under {@code /api/sqs/…}.
 *
 * <p>Each route advertises a CLI command name and its parameters via {@code /api/status}, so the
 * CLI can build {@code clm sqs <command>} dynamically with no compile-time knowledge of SQS.
 * Responses are synthetic and stateless, mirroring the contract mocking the stubs already provide.
 *
 * <p>Discovered via {@code META-INF/services/io.cloudmock.core.spi.CloudMockApiService}.
 */
public class CloudMockSqsApiService implements CloudMockApiService {

    private static final ApiParam QUEUE = new ApiParam("queue", true, "Queue name");

    @Override
    public String serviceId() {
        return "sqs";
    }

    @Override
    public void registerRoutes(ApiRouteRegistrar r) {
        r.register(HttpMethod.GET, "/list-queues", "list-queues",
                "List SQS queues", List.of(), this::listQueues);
        r.register(HttpMethod.POST, "/send-message", "send-message",
                "Send a message to an SQS queue",
                List.of(QUEUE, new ApiParam("body", true, "Message body")), this::sendMessage);
        r.register(HttpMethod.GET, "/receive-message", "receive-message",
                "Receive a message from an SQS queue", List.of(QUEUE), this::receiveMessage);
        r.register(HttpMethod.POST, "/purge-queue", "purge-queue",
                "Purge all messages from an SQS queue", List.of(QUEUE), this::purgeQueue);
    }

    private ApiResponse listQueues(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "queues", List.of("http://localhost/000000000000/queue")));
    }

    private ApiResponse sendMessage(ApiRequest req) {
        String body = req.queryParams().getOrDefault("body", "");
        return new ApiResponse(200, Map.of(
                "messageId", UUID.randomUUID().toString(),
                "md5OfBody", SqsJson.md5(body)));
    }

    private ApiResponse receiveMessage(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "messages", List.of(Map.of(
                        "messageId", UUID.randomUUID().toString(),
                        "receiptHandle", UUID.randomUUID().toString(),
                        "body", "cloudmock-synthetic-message"))));
    }

    private ApiResponse purgeQueue(ApiRequest req) {
        return new ApiResponse(200, Map.of(
                "status", "purged",
                "queue", req.queryParams().getOrDefault("queue", "")));
    }
}

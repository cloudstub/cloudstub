package io.cloudstub.sns;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.restapi.ApiParam;
import io.cloudstub.core.spi.restapi.ApiRequest;
import io.cloudstub.core.spi.restapi.ApiResponse;
import io.cloudstub.core.spi.restapi.CloudStubApiContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API surface for SNS, mounted under {@code /api/sns/…}.
 *
 * <p>Each route reads and writes the shared {@link StateStore} under the same {@link SnsKeys}
 * scheme as the AWS-protocol stubs, so a topic created over the AWS protocol is returned by {@code
 * GET /api/sns/list-topics} and vice versa. Each route also advertises its command name and
 * parameters via {@code /api/status}.
 */
public class CloudStubSNSApiService implements CloudStubApiService {

    private static final ApiParam TOPIC = new ApiParam("topic", true, "Topic name");

    private StateStore store;

    @Override
    public String serviceId() {
        return "sns";
    }

    @Override
    public void registerRoutes(CloudStubApiContext context) {
        this.store = context.stateStore();
        var r = context.registrar();
        r.register(
                HttpMethod.GET,
                "/list-topics",
                "list-topics",
                "List SNS topics",
                List.of(),
                this::listTopics);
        r.register(
                HttpMethod.POST,
                "/create-topic",
                "create-topic",
                "Create an SNS topic",
                List.of(TOPIC),
                this::createTopic);
        r.register(
                HttpMethod.POST,
                "/delete-topic",
                "delete-topic",
                "Delete an SNS topic and its subscriptions",
                List.of(TOPIC),
                this::deleteTopic);
        r.register(
                HttpMethod.POST,
                "/subscribe",
                "subscribe",
                "Subscribe an endpoint to an SNS topic",
                List.of(
                        TOPIC,
                        new ApiParam("protocol", true, "Subscription protocol, e.g. sqs or https"),
                        new ApiParam("endpoint", true, "Subscription endpoint (ARN or URL)")),
                this::subscribe);
        r.register(
                HttpMethod.GET,
                "/list-subscriptions",
                "list-subscriptions",
                "List subscriptions for an SNS topic",
                List.of(TOPIC),
                this::listSubscriptions);
        r.register(
                HttpMethod.POST,
                "/publish",
                "publish",
                "Publish a message to an SNS topic (acknowledged, not delivered)",
                List.of(TOPIC, new ApiParam("message", true, "Message body")),
                this::publish);
    }

    private ApiResponse listTopics(ApiRequest req) {
        List<String> topics = new ArrayList<>();
        for (String key : store.list(SnsKeys.TOPICS_PREFIX)) {
            if (!SnsKeys.isTopicMarkerKey(key)) {
                continue;
            }
            Object arn = store.get(key);
            if (arn != null) {
                topics.add(arn.toString());
            }
        }
        return new ApiResponse(200, Map.of("topics", topics));
    }

    private ApiResponse createTopic(ApiRequest req) {
        String name = req.queryParams().getOrDefault("topic", "");
        String arn = SnsKeys.topicArn(name);
        store.put(SnsKeys.topicKey(name), arn);
        return new ApiResponse(200, Map.of("topicArn", arn));
    }

    private ApiResponse deleteTopic(ApiRequest req) {
        String name = req.queryParams().getOrDefault("topic", "");
        store.delete(SnsKeys.topicKey(name));
        store.clear(SnsKeys.subscriptionPrefix(name));
        return new ApiResponse(200, Map.of("status", "deleted", "topic", name));
    }

    private ApiResponse subscribe(ApiRequest req) {
        String name = req.queryParams().getOrDefault("topic", "");
        String protocol = req.queryParams().getOrDefault("protocol", "");
        String endpoint = req.queryParams().getOrDefault("endpoint", "");
        String id = UUID.randomUUID().toString();
        String topicArn = SnsKeys.topicArn(name);
        String subscriptionArn = topicArn + ":" + id;
        store.put(
                SnsKeys.subscriptionKey(name, id),
                Map.of(
                        "subscriptionArn", subscriptionArn,
                        "protocol", protocol,
                        "endpoint", endpoint,
                        "topicArn", topicArn));
        return new ApiResponse(200, Map.of("subscriptionArn", subscriptionArn));
    }

    private ApiResponse listSubscriptions(ApiRequest req) {
        String name = req.queryParams().getOrDefault("topic", "");
        List<Map<String, Object>> subscriptions = new ArrayList<>();
        for (String key : store.list(SnsKeys.subscriptionPrefix(name))) {
            Object record = store.get(key);
            if (record instanceof Map<?, ?> map) {
                subscriptions.add(
                        Map.of(
                                "subscriptionArn", String.valueOf(map.get("subscriptionArn")),
                                "protocol", String.valueOf(map.get("protocol")),
                                "endpoint", String.valueOf(map.get("endpoint"))));
            }
        }
        return new ApiResponse(200, Map.of("subscriptions", subscriptions));
    }

    private ApiResponse publish(ApiRequest req) {
        String messageId = UUID.randomUUID().toString();
        return new ApiResponse(200, Map.of("messageId", messageId));
    }
}

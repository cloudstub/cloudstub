package io.cloudstub.sns;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubRequest;
import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.StubTemplates;
import java.util.Map;
import java.util.UUID;

/**
 * CloudStub service module for Amazon SNS.
 *
 * <p>Uses the AWS Query (XML / Form) protocol: each operation is matched by its {@code Action} form
 * parameter via {@link StubRegistrar#registerXmlFormStub}.
 *
 * <p>The topic and subscription operations are state-backed: each handler reads and writes the
 * shared {@link StateStore} under the {@code sns/} prefix, so a topic created by {@code
 * CreateTopic} is returned by {@code ListTopics} and a subscription by {@code
 * ListSubscriptionsByTopic}. The remaining operations are served from static Handlebars templates
 * and return well-formed but stateless responses.
 *
 * <p>A {@code Publish} is acknowledged with a {@code MessageId} but not delivered to subscribers,
 * and a {@code Subscribe} is recorded as already confirmed ({@code ConfirmSubscription} is a
 * no-op).
 */
public class CloudStubSNSService implements CloudStubService {

    private static final String SERVICE_ID = "sns";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudStubContext context) {
        StubRegistrar registrar = context.registrar();
        registrar.registerXmlFormStub(
                "AddPermission", StubTemplates.load(CloudStubSNSService.class, "AddPermission"));
        registrar.registerXmlFormStub(
                "CheckIfPhoneNumberIsOptedOut",
                StubTemplates.load(CloudStubSNSService.class, "CheckIfPhoneNumberIsOptedOut"));
        registrar.registerXmlFormStub(
                "ConfirmSubscription",
                StubTemplates.load(CloudStubSNSService.class, "ConfirmSubscription"));
        registrar.registerXmlFormStub(
                "CreatePlatformApplication",
                StubTemplates.load(CloudStubSNSService.class, "CreatePlatformApplication"));
        registrar.registerXmlFormStub(
                "CreatePlatformEndpoint",
                StubTemplates.load(CloudStubSNSService.class, "CreatePlatformEndpoint"));
        registrar.registerXmlFormStub(
                "CreateSMSSandboxPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "CreateSMSSandboxPhoneNumber"));
        registrar.registerXmlFormStub("CreateTopic", this::createTopic);
        registrar.registerXmlFormStub(
                "DeleteEndpoint", StubTemplates.load(CloudStubSNSService.class, "DeleteEndpoint"));
        registrar.registerXmlFormStub(
                "DeletePlatformApplication",
                StubTemplates.load(CloudStubSNSService.class, "DeletePlatformApplication"));
        registrar.registerXmlFormStub(
                "DeleteSMSSandboxPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "DeleteSMSSandboxPhoneNumber"));
        registrar.registerXmlFormStub("DeleteTopic", this::deleteTopic);
        registrar.registerXmlFormStub(
                "GetDataProtectionPolicy",
                StubTemplates.load(CloudStubSNSService.class, "GetDataProtectionPolicy"));
        registrar.registerXmlFormStub(
                "GetEndpointAttributes",
                StubTemplates.load(CloudStubSNSService.class, "GetEndpointAttributes"));
        registrar.registerXmlFormStub(
                "GetPlatformApplicationAttributes",
                StubTemplates.load(CloudStubSNSService.class, "GetPlatformApplicationAttributes"));
        registrar.registerXmlFormStub(
                "GetSMSAttributes",
                StubTemplates.load(CloudStubSNSService.class, "GetSMSAttributes"));
        registrar.registerXmlFormStub(
                "GetSMSSandboxAccountStatus",
                StubTemplates.load(CloudStubSNSService.class, "GetSMSSandboxAccountStatus"));
        registrar.registerXmlFormStub("GetSubscriptionAttributes", this::getSubscriptionAttributes);
        registrar.registerXmlFormStub("GetTopicAttributes", this::getTopicAttributes);
        registrar.registerXmlFormStub(
                "ListEndpointsByPlatformApplication",
                StubTemplates.load(
                        CloudStubSNSService.class, "ListEndpointsByPlatformApplication"));
        registrar.registerXmlFormStub(
                "ListOriginationNumbers",
                StubTemplates.load(CloudStubSNSService.class, "ListOriginationNumbers"));
        registrar.registerXmlFormStub(
                "ListPhoneNumbersOptedOut",
                StubTemplates.load(CloudStubSNSService.class, "ListPhoneNumbersOptedOut"));
        registrar.registerXmlFormStub(
                "ListPlatformApplications",
                StubTemplates.load(CloudStubSNSService.class, "ListPlatformApplications"));
        registrar.registerXmlFormStub(
                "ListSMSSandboxPhoneNumbers",
                StubTemplates.load(CloudStubSNSService.class, "ListSMSSandboxPhoneNumbers"));
        registrar.registerXmlFormStub("ListSubscriptions", this::listSubscriptions);
        registrar.registerXmlFormStub("ListSubscriptionsByTopic", this::listSubscriptionsByTopic);
        registrar.registerXmlFormStub(
                "ListTagsForResource",
                StubTemplates.load(CloudStubSNSService.class, "ListTagsForResource"));
        registrar.registerXmlFormStub("ListTopics", this::listTopics);
        registrar.registerXmlFormStub(
                "OptInPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "OptInPhoneNumber"));
        registrar.registerXmlFormStub(
                "Publish", StubTemplates.load(CloudStubSNSService.class, "Publish"));
        registrar.registerXmlFormStub(
                "PublishBatch", StubTemplates.load(CloudStubSNSService.class, "PublishBatch"));
        registrar.registerXmlFormStub(
                "PutDataProtectionPolicy",
                StubTemplates.load(CloudStubSNSService.class, "PutDataProtectionPolicy"));
        registrar.registerXmlFormStub(
                "RemovePermission",
                StubTemplates.load(CloudStubSNSService.class, "RemovePermission"));
        registrar.registerXmlFormStub(
                "SetEndpointAttributes",
                StubTemplates.load(CloudStubSNSService.class, "SetEndpointAttributes"));
        registrar.registerXmlFormStub(
                "SetPlatformApplicationAttributes",
                StubTemplates.load(CloudStubSNSService.class, "SetPlatformApplicationAttributes"));
        registrar.registerXmlFormStub(
                "SetSMSAttributes",
                StubTemplates.load(CloudStubSNSService.class, "SetSMSAttributes"));
        registrar.registerXmlFormStub(
                "SetSubscriptionAttributes",
                StubTemplates.load(CloudStubSNSService.class, "SetSubscriptionAttributes"));
        registrar.registerXmlFormStub(
                "SetTopicAttributes",
                StubTemplates.load(CloudStubSNSService.class, "SetTopicAttributes"));
        registrar.registerXmlFormStub("Subscribe", this::subscribe);
        registrar.registerXmlFormStub(
                "TagResource", StubTemplates.load(CloudStubSNSService.class, "TagResource"));
        registrar.registerXmlFormStub("Unsubscribe", this::unsubscribe);
        registrar.registerXmlFormStub(
                "UntagResource", StubTemplates.load(CloudStubSNSService.class, "UntagResource"));
        registrar.registerXmlFormStub(
                "VerifySMSSandboxPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "VerifySMSSandboxPhoneNumber"));
    }

    private StubResponse createTopic(StubRequest req, StateStore store) {
        String name = required(SnsForm.parse(req.body()), "Name");
        if (name == null) {
            return missing("Name");
        }
        String arn = SnsKeys.topicArn(name);
        store.put(SnsKeys.topicKey(name), arn);
        return StubResponse.xml(
                SnsXml.result("CreateTopic", "<TopicArn>" + SnsXml.escape(arn) + "</TopicArn>"));
    }

    private StubResponse deleteTopic(StubRequest req, StateStore store) {
        String topicArn = required(SnsForm.parse(req.body()), "TopicArn");
        if (topicArn == null) {
            return missing("TopicArn");
        }
        String name = SnsKeys.topicNameFromArn(topicArn);
        store.delete(SnsKeys.topicKey(name));
        store.clear(SnsKeys.subscriptionPrefix(name));
        return StubResponse.xml(SnsXml.empty("DeleteTopic"));
    }

    private StubResponse listTopics(StubRequest req, StateStore store) {
        StringBuilder members = new StringBuilder();
        for (String key : store.list(SnsKeys.TOPICS_PREFIX)) {
            if (!SnsKeys.isTopicMarkerKey(key)) {
                continue;
            }
            Object arn = store.get(key);
            if (arn == null) {
                continue;
            }
            members.append("<member><TopicArn>")
                    .append(SnsXml.escape(arn.toString()))
                    .append("</TopicArn></member>");
        }
        return StubResponse.xml(SnsXml.result("ListTopics", "<Topics>" + members + "</Topics>"));
    }

    private StubResponse getTopicAttributes(StubRequest req, StateStore store) {
        String arn = required(SnsForm.parse(req.body()), "TopicArn");
        if (arn == null) {
            return missing("TopicArn");
        }
        String name = SnsKeys.topicNameFromArn(arn);
        int subscriptions = store.list(SnsKeys.subscriptionPrefix(name)).size();
        String attributes =
                entry("TopicArn", arn)
                        + entry("Owner", SnsKeys.ACCOUNT)
                        + entry("SubscriptionsConfirmed", Integer.toString(subscriptions))
                        + entry("SubscriptionsPending", "0")
                        + entry("SubscriptionsDeleted", "0")
                        + entry("DisplayName", "");
        return StubResponse.xml(
                SnsXml.result("GetTopicAttributes", "<Attributes>" + attributes + "</Attributes>"));
    }

    private StubResponse subscribe(StubRequest req, StateStore store) {
        Map<String, String> form = SnsForm.parse(req.body());
        String topicArn = required(form, "TopicArn");
        if (topicArn == null) {
            return missing("TopicArn");
        }
        String name = SnsKeys.topicNameFromArn(topicArn);
        String id = UUID.randomUUID().toString();
        String subscriptionArn = topicArn + ":" + id;
        store.put(
                SnsKeys.subscriptionKey(name, id),
                Map.of(
                        "subscriptionArn",
                        subscriptionArn,
                        "protocol",
                        orEmpty(form.get("Protocol")),
                        "endpoint",
                        orEmpty(form.get("Endpoint")),
                        "topicArn",
                        topicArn));
        return StubResponse.xml(
                SnsXml.result(
                        "Subscribe",
                        "<SubscriptionArn>"
                                + SnsXml.escape(subscriptionArn)
                                + "</SubscriptionArn>"));
    }

    private StubResponse unsubscribe(StubRequest req, StateStore store) {
        String subscriptionArn = required(SnsForm.parse(req.body()), "SubscriptionArn");
        if (subscriptionArn == null) {
            return missing("SubscriptionArn");
        }
        int lastColon = subscriptionArn.lastIndexOf(':');
        if (lastColon < 0) {
            return invalid("SubscriptionArn");
        }
        String id = subscriptionArn.substring(lastColon + 1);
        String name = SnsKeys.topicNameFromArn(subscriptionArn.substring(0, lastColon));
        store.delete(SnsKeys.subscriptionKey(name, id));
        return StubResponse.xml(SnsXml.empty("Unsubscribe"));
    }

    private StubResponse listSubscriptions(StubRequest req, StateStore store) {
        StringBuilder members = new StringBuilder();
        for (String topicKey : store.list(SnsKeys.TOPICS_PREFIX)) {
            if (!SnsKeys.isTopicMarkerKey(topicKey)) {
                continue;
            }
            String name = topicKey.substring(SnsKeys.TOPICS_PREFIX.length());
            appendSubscriptions(members, store, name);
        }
        return StubResponse.xml(
                SnsXml.result(
                        "ListSubscriptions", "<Subscriptions>" + members + "</Subscriptions>"));
    }

    private StubResponse listSubscriptionsByTopic(StubRequest req, StateStore store) {
        String topicArn = required(SnsForm.parse(req.body()), "TopicArn");
        if (topicArn == null) {
            return missing("TopicArn");
        }
        String name = SnsKeys.topicNameFromArn(topicArn);
        StringBuilder members = new StringBuilder();
        appendSubscriptions(members, store, name);
        return StubResponse.xml(
                SnsXml.result(
                        "ListSubscriptionsByTopic",
                        "<Subscriptions>" + members + "</Subscriptions>"));
    }

    private StubResponse getSubscriptionAttributes(StubRequest req, StateStore store) {
        String subscriptionArn = required(SnsForm.parse(req.body()), "SubscriptionArn");
        if (subscriptionArn == null) {
            return missing("SubscriptionArn");
        }
        int lastColon = subscriptionArn.lastIndexOf(':');
        if (lastColon < 0) {
            return invalid("SubscriptionArn");
        }
        String id = subscriptionArn.substring(lastColon + 1);
        String topicArn = subscriptionArn.substring(0, lastColon);
        String name = SnsKeys.topicNameFromArn(topicArn);
        Map<?, ?> record = asMap(store.get(SnsKeys.subscriptionKey(name, id)));
        String attributes =
                entry("SubscriptionArn", subscriptionArn)
                        + entry("TopicArn", topicArn)
                        + entry("Owner", SnsKeys.ACCOUNT)
                        + entry("Protocol", recordValue(record, "protocol"))
                        + entry("Endpoint", recordValue(record, "endpoint"))
                        + entry("ConfirmationWasAuthenticated", "true")
                        + entry("RawMessageDelivery", "false");
        return StubResponse.xml(
                SnsXml.result(
                        "GetSubscriptionAttributes",
                        "<Attributes>" + attributes + "</Attributes>"));
    }

    private void appendSubscriptions(StringBuilder members, StateStore store, String name) {
        String topicArn = SnsKeys.topicArn(name);
        for (String key : store.list(SnsKeys.subscriptionPrefix(name))) {
            Map<?, ?> record = asMap(store.get(key));
            if (record == null) {
                continue;
            }
            members.append("<member>")
                    .append(tag("SubscriptionArn", recordValue(record, "subscriptionArn")))
                    .append(tag("Owner", SnsKeys.ACCOUNT))
                    .append(tag("Protocol", recordValue(record, "protocol")))
                    .append(tag("Endpoint", recordValue(record, "endpoint")))
                    .append(tag("TopicArn", topicArn))
                    .append("</member>");
        }
    }

    /** An SNS attribute map {@code <entry><key>..</key><value>..</value></entry>}. */
    private static String entry(String key, String value) {
        return "<entry><key>"
                + SnsXml.escape(key)
                + "</key><value>"
                + SnsXml.escape(value)
                + "</value></entry>";
    }

    private static String tag(String name, String value) {
        return "<" + name + ">" + SnsXml.escape(value) + "</" + name + ">";
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String recordValue(Map<?, ?> record, String key) {
        if (record == null) {
            return "";
        }
        Object value = record.get(key);
        return value == null ? "" : value.toString();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** A required form value, or {@code null} when absent or blank. */
    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static StubResponse missing(String parameter) {
        return badRequest("Missing required parameter: " + parameter);
    }

    private static StubResponse invalid(String parameter) {
        return badRequest("Invalid parameter: " + parameter);
    }

    private static StubResponse badRequest(String message) {
        return StubResponse.of(
                400, StubResponse.CONTENT_TYPE_XML, SnsXml.error("InvalidParameter", message));
    }
}

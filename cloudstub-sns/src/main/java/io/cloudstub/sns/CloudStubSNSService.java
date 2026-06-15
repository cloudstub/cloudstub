package io.cloudstub.sns;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.StubRegistrar;
import io.cloudstub.core.spi.StubTemplates;

/**
 * CloudStub service module for SNS.
 *
 * <p><strong>GENERATED — HUMAN REVIEW REQUIRED.</strong> Response templates are minimal
 * placeholders in {@code src/main/resources/templates/}. Replace each {@code .hbs} file with a
 * well-formed Handlebars response that the AWS SDK can parse without error. See existing modules
 * (cloudstub-sqs, cloudstub-secretsmanager) for examples.
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
        registrar.registerXmlFormStub(
                "CreateTopic", StubTemplates.load(CloudStubSNSService.class, "CreateTopic"));
        registrar.registerXmlFormStub(
                "DeleteEndpoint", StubTemplates.load(CloudStubSNSService.class, "DeleteEndpoint"));
        registrar.registerXmlFormStub(
                "DeletePlatformApplication",
                StubTemplates.load(CloudStubSNSService.class, "DeletePlatformApplication"));
        registrar.registerXmlFormStub(
                "DeleteSMSSandboxPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "DeleteSMSSandboxPhoneNumber"));
        registrar.registerXmlFormStub(
                "DeleteTopic", StubTemplates.load(CloudStubSNSService.class, "DeleteTopic"));
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
        registrar.registerXmlFormStub(
                "GetSubscriptionAttributes",
                StubTemplates.load(CloudStubSNSService.class, "GetSubscriptionAttributes"));
        registrar.registerXmlFormStub(
                "GetTopicAttributes",
                StubTemplates.load(CloudStubSNSService.class, "GetTopicAttributes"));
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
        registrar.registerXmlFormStub(
                "ListSubscriptions",
                StubTemplates.load(CloudStubSNSService.class, "ListSubscriptions"));
        registrar.registerXmlFormStub(
                "ListSubscriptionsByTopic",
                StubTemplates.load(CloudStubSNSService.class, "ListSubscriptionsByTopic"));
        registrar.registerXmlFormStub(
                "ListTagsForResource",
                StubTemplates.load(CloudStubSNSService.class, "ListTagsForResource"));
        registrar.registerXmlFormStub(
                "ListTopics", StubTemplates.load(CloudStubSNSService.class, "ListTopics"));
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
        registrar.registerXmlFormStub(
                "Subscribe", StubTemplates.load(CloudStubSNSService.class, "Subscribe"));
        registrar.registerXmlFormStub(
                "TagResource", StubTemplates.load(CloudStubSNSService.class, "TagResource"));
        registrar.registerXmlFormStub(
                "Unsubscribe", StubTemplates.load(CloudStubSNSService.class, "Unsubscribe"));
        registrar.registerXmlFormStub(
                "UntagResource", StubTemplates.load(CloudStubSNSService.class, "UntagResource"));
        registrar.registerXmlFormStub(
                "VerifySMSSandboxPhoneNumber",
                StubTemplates.load(CloudStubSNSService.class, "VerifySMSSandboxPhoneNumber"));
    }
}

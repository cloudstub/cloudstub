package io.cloudmock.sns;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * CloudMock service module for SNS.
 *
 * <p><strong>GENERATED — HUMAN REVIEW REQUIRED.</strong>
 * Response templates are minimal placeholders in {@code src/main/resources/templates/}.
 * Replace each {@code .hbs} file with a well-formed Handlebars response that the AWS SDK
 * can parse without error. See existing modules (cloudmock-sqs, cloudmock-secretsmanager)
 * for examples.
 */
public class CloudMockSNSService implements CloudMockService {

    private static final String SERVICE_ID = "sns";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudMockContext context) {
        StubRegistrar registrar = context.registrar();
        registrar.registerXmlFormStub("AddPermission", loadTemplate("AddPermission"));
        registrar.registerXmlFormStub("CheckIfPhoneNumberIsOptedOut", loadTemplate("CheckIfPhoneNumberIsOptedOut"));
        registrar.registerXmlFormStub("ConfirmSubscription", loadTemplate("ConfirmSubscription"));
        registrar.registerXmlFormStub("CreatePlatformApplication", loadTemplate("CreatePlatformApplication"));
        registrar.registerXmlFormStub("CreatePlatformEndpoint", loadTemplate("CreatePlatformEndpoint"));
        registrar.registerXmlFormStub("CreateSMSSandboxPhoneNumber", loadTemplate("CreateSMSSandboxPhoneNumber"));
        registrar.registerXmlFormStub("CreateTopic", loadTemplate("CreateTopic"));
        registrar.registerXmlFormStub("DeleteEndpoint", loadTemplate("DeleteEndpoint"));
        registrar.registerXmlFormStub("DeletePlatformApplication", loadTemplate("DeletePlatformApplication"));
        registrar.registerXmlFormStub("DeleteSMSSandboxPhoneNumber", loadTemplate("DeleteSMSSandboxPhoneNumber"));
        registrar.registerXmlFormStub("DeleteTopic", loadTemplate("DeleteTopic"));
        registrar.registerXmlFormStub("GetDataProtectionPolicy", loadTemplate("GetDataProtectionPolicy"));
        registrar.registerXmlFormStub("GetEndpointAttributes", loadTemplate("GetEndpointAttributes"));
        registrar.registerXmlFormStub("GetPlatformApplicationAttributes", loadTemplate("GetPlatformApplicationAttributes"));
        registrar.registerXmlFormStub("GetSMSAttributes", loadTemplate("GetSMSAttributes"));
        registrar.registerXmlFormStub("GetSMSSandboxAccountStatus", loadTemplate("GetSMSSandboxAccountStatus"));
        registrar.registerXmlFormStub("GetSubscriptionAttributes", loadTemplate("GetSubscriptionAttributes"));
        registrar.registerXmlFormStub("GetTopicAttributes", loadTemplate("GetTopicAttributes"));
        registrar.registerXmlFormStub("ListEndpointsByPlatformApplication", loadTemplate("ListEndpointsByPlatformApplication"));
        registrar.registerXmlFormStub("ListOriginationNumbers", loadTemplate("ListOriginationNumbers"));
        registrar.registerXmlFormStub("ListPhoneNumbersOptedOut", loadTemplate("ListPhoneNumbersOptedOut"));
        registrar.registerXmlFormStub("ListPlatformApplications", loadTemplate("ListPlatformApplications"));
        registrar.registerXmlFormStub("ListSMSSandboxPhoneNumbers", loadTemplate("ListSMSSandboxPhoneNumbers"));
        registrar.registerXmlFormStub("ListSubscriptions", loadTemplate("ListSubscriptions"));
        registrar.registerXmlFormStub("ListSubscriptionsByTopic", loadTemplate("ListSubscriptionsByTopic"));
        registrar.registerXmlFormStub("ListTagsForResource", loadTemplate("ListTagsForResource"));
        registrar.registerXmlFormStub("ListTopics", loadTemplate("ListTopics"));
        registrar.registerXmlFormStub("OptInPhoneNumber", loadTemplate("OptInPhoneNumber"));
        registrar.registerXmlFormStub("Publish", loadTemplate("Publish"));
        registrar.registerXmlFormStub("PublishBatch", loadTemplate("PublishBatch"));
        registrar.registerXmlFormStub("PutDataProtectionPolicy", loadTemplate("PutDataProtectionPolicy"));
        registrar.registerXmlFormStub("RemovePermission", loadTemplate("RemovePermission"));
        registrar.registerXmlFormStub("SetEndpointAttributes", loadTemplate("SetEndpointAttributes"));
        registrar.registerXmlFormStub("SetPlatformApplicationAttributes", loadTemplate("SetPlatformApplicationAttributes"));
        registrar.registerXmlFormStub("SetSMSAttributes", loadTemplate("SetSMSAttributes"));
        registrar.registerXmlFormStub("SetSubscriptionAttributes", loadTemplate("SetSubscriptionAttributes"));
        registrar.registerXmlFormStub("SetTopicAttributes", loadTemplate("SetTopicAttributes"));
        registrar.registerXmlFormStub("Subscribe", loadTemplate("Subscribe"));
        registrar.registerXmlFormStub("TagResource", loadTemplate("TagResource"));
        registrar.registerXmlFormStub("Unsubscribe", loadTemplate("Unsubscribe"));
        registrar.registerXmlFormStub("UntagResource", loadTemplate("UntagResource"));
        registrar.registerXmlFormStub("VerifySMSSandboxPhoneNumber", loadTemplate("VerifySMSSandboxPhoneNumber"));
    }

    private static String loadTemplate(String name) {
        String path = "/templates/" + name + ".hbs";
        try (InputStream in = CloudMockSNSService.class.getResourceAsStream(path)) {
            if (in == null)
                throw new IllegalStateException("Template not found: " + path);
            return new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

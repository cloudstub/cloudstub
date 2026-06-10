package io.cloudmock.example.sdkv1;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

/**
 * Worked example of the <strong>bring-your-own-stub</strong> path for AWS SDK v1.
 *
 * <p>The {@code cloudmock-sdk-v1} companion only redirects the endpoint. Most first-party CloudMock
 * modules target the SDK v2 protocol shape (JSON / {@code X-Amz-Target}), which SDK v1's XML/QUERY
 * {@code Action}-form requests do not match — so a v1 call against those services returns 404. (SNS
 * is the exception: it uses XML/QUERY in both v1 and v2, so {@code cloudmock-sns} would also serve
 * this call; it is deliberately kept off this test's classpath to isolate the bring-your-own-stub
 * path.) A v1 user closes the gap by authoring their own {@link CloudMockService} and registering
 * stubs via {@link StubRegistrar#registerXmlFormStub(String, String)} — exactly as this class does
 * for the SNS {@code Publish} action.
 *
 * <p>Install it explicitly with {@code new CloudMockExtension().withService(new SnsV1PublishStubService())};
 * see {@code module-authoring.md} for the full SPI walkthrough.
 */
public final class SnsV1PublishStubService implements CloudMockService {

    // The QUERY-protocol XML response shape AWS SDK v1 SNS expects from a Publish call. The
    // {{randomValue}} Handlebars helper yields a fresh MessageId per request.
    private static final String PUBLISH_RESPONSE = """
            <PublishResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
              <PublishResult>
                <MessageId>{{randomValue type='UUID'}}</MessageId>
              </PublishResult>
              <ResponseMetadata><RequestId>{{randomValue type='UUID'}}</RequestId></ResponseMetadata>
            </PublishResponse>""";

    @Override
    public String serviceId() {
        return "sns";
    }

    @Override
    public void register(CloudMockContext context) {
        context.registrar().registerXmlFormStub("Publish", PUBLISH_RESPONSE);
    }
}

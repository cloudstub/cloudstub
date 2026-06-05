package io.cloudmock.core;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for XML/Form ({@code Action=}) routing in {@code WireMockStubRegistrar}.
 *
 * <p>Pins the fix for the substring-matching fragility found in the issue #0020 review: a stub
 * registered for action {@code Publish} must match a request for {@code Publish} only — never one for
 * {@code PublishBatch} (whose action name has {@code Publish} as a prefix). The old
 * {@code containing("Action=Publish")} matcher over-matched and relied on registration order plus
 * WireMock's last-wins tie-break to stay correct; the hardened matcher anchors on parameter
 * boundaries instead.
 */
class XmlFormRoutingTest {

    private final CloudMock cloudMock = new CloudMock();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** Registers ONLY the {@code Publish} action — deliberately not {@code PublishBatch}. */
    private static final class PublishOnlyService implements CloudMockService {
        @Override public String serviceId() { return "publish-only"; }
        @Override public void register(CloudMockContext context) {
            context.registrar().registerXmlFormStub("Publish", "<PublishResponse/>");
        }
    }

    private HttpResponse<String> postForm(String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void exactActionIsMatched() throws Exception {
        cloudMock.withService(new PublishOnlyService());
        cloudMock.start();

        HttpResponse<String> response = postForm("Action=Publish&Version=2010-03-31");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("PublishResponse"));
    }

    /**
     * The regression: a {@code Publish}-only stub must NOT serve a {@code PublishBatch} request.
     * With the old {@code containing("Action=Publish")} matcher this returned 200 (wrong); the
     * hardened, boundary-anchored matcher leaves it unmatched (404).
     */
    @Test
    void prefixActionDoesNotMatchLongerAction() throws Exception {
        cloudMock.withService(new PublishOnlyService());
        cloudMock.start();

        HttpResponse<String> response = postForm("Action=PublishBatch&Version=2010-03-31");

        assertEquals(404, response.statusCode(),
                "Publish stub must not match a PublishBatch request");
    }

    /** The action need not be the first form parameter — the left boundary is start-of-body OR '&'. */
    @Test
    void actionMatchedWhenNotFirstParameter() throws Exception {
        cloudMock.withService(new PublishOnlyService());
        cloudMock.start();

        HttpResponse<String> response = postForm("TopicArn=arn:aws:sns:us-east-1:000000000000:t&Action=Publish");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("PublishResponse"));
    }
}

package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubRegistrar;

import java.net.HttpURLConnection;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.cloudmock.core.internal.HttpConstants.*;

/**
 * Internal {@link StubRegistrar} implementation that delegates to a live WireMock server.
 * Never exposed outside {@code io.cloudmock.core.internal}.
 *
 * <p>Each registration is also recorded in the {@link ServiceRegistry} under the current
 * service ID so that {@link FaultEngine} can later generate matching fault stubs.
 */
public final class WireMockStubRegistrar implements StubRegistrar {

    private final WireMockServer server;
    private final ServiceRegistry registry = new ServiceRegistry();
    private String currentServiceId;

    public WireMockStubRegistrar(WireMockServer server) {
        this.server = server;
    }

    /** Creates a {@link FaultEngine} backed by this registrar's stub registry. */
    public FaultEngine newFaultEngine() {
        return new FaultEngine(server, registry);
    }

    /** Called by {@code CloudMock} before each service module registers its stubs. */
    public void setCurrentService(String serviceId) {
        this.currentServiceId = serviceId;
    }

    @Override
    public void registerXmlFormStub(String actionName, String responseTemplate) {
        // Match Action=<name> as a *complete* form parameter, not a substring. A plain
        // containing("Action=Publish") would also match an "Action=PublishBatch" body, leaving
        // correctness dependent on registration order + WireMock's last-wins tie-break. Anchoring
        // the value to a parameter boundary (start-of-body or '&' on the left, '&' or end on the
        // right) removes that fragility. Full-match semantics (RegexPattern), so anchor both sides.
        String actionPattern = "(?s)(.*&)?Action=" + Pattern.quote(actionName) + "(&.*)?";
        server.stubFor(post(anyUrl())
                .withRequestBody(matching(actionPattern))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_XML_UTF8)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.FORM_URL, actionName, responseTemplate,
                    CONTENT_TYPE_XML_UTF8, HttpURLConnection.HTTP_OK));
        }
    }

    @Override
    public void registerJsonTargetStub(String target, String responseTemplate) {
        server.stubFor(post(anyUrl())
                .withHeader(HEADER_AMZ_TARGET, equalTo(target))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_AMZ_JSON_1_1)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.JSON_TARGET, target, responseTemplate,
                    CONTENT_TYPE_AMZ_JSON_1_1, HttpURLConnection.HTTP_OK));
        }
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate) {
        server.stubFor(request(method.name(), urlMatching(pathPattern))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.REST, method.name() + " " + pathPattern, responseTemplate,
                    "application/octet-stream", HttpURLConnection.HTTP_OK));
        }
    }
}

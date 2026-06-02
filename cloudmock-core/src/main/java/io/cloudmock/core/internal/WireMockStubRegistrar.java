package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubRegistrar;

import java.net.HttpURLConnection;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.cloudmock.core.internal.HttpConstants.*;

/**
 * Internal {@link StubRegistrar} implementation that delegates to a live WireMock server.
 * Never exposed outside {@code io.cloudmock.core.internal}.
 */
public final class WireMockStubRegistrar implements StubRegistrar {

    private final WireMockServer server;

    public WireMockStubRegistrar(WireMockServer server) {
        this.server = server;
    }

    @Override
    public void registerXmlFormStub(String actionName, String responseTemplate) {
        server.stubFor(post(anyUrl())
                .withRequestBody(containing("Action=" + actionName))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_XML_UTF8)
                        .withBody(responseTemplate)));
    }

    @Override
    public void registerJsonTargetStub(String target, String responseTemplate) {
        server.stubFor(post(anyUrl())
                .withHeader(HEADER_AMZ_TARGET, equalTo(target))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_AMZ_JSON_1_1)
                        .withBody(responseTemplate)));
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate) {
        server.stubFor(request(method.name(), urlMatching(pathPattern))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withBody(responseTemplate)));
    }
}

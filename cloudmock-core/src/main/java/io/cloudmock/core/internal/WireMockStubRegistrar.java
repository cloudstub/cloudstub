package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubRegistrar;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
        server.stubFor(post(urlEqualTo("/"))
                .withRequestBody(containing("Action=" + actionName))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(responseTemplate)));
    }

    @Override
    public void registerJsonTargetStub(String target, String responseTemplate) {
        server.stubFor(post(anyUrl())
                .withHeader("X-Amz-Target", equalTo(target))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(responseTemplate)));
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate) {
        server.stubFor(request(method.name(), urlMatching(pathPattern))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(responseTemplate)));
    }
}

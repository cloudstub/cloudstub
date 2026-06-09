package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import io.cloudmock.core.restapi.ModuleStatus;
import io.cloudmock.core.restapi.StubInfo;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubHandler;
import io.cloudmock.core.spi.StubRegistrar;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.cloudmock.core.internal.HttpConstants.*;

/**
 * Internal {@link StubRegistrar} implementation that delegates to a live WireMock server.
 * Never exposed outside {@code io.cloudmock.core.internal}.
 *
 * <p>Each registration is also recorded in the {@link ServiceRegistry} under the current
 * service ID so that {@link FaultEngine} can later generate matching fault stubs.
 *
 * <p>Stubs are named {@code cloudmock:<serviceId>:<matchKey>} so that
 * {@code CloudMock.requestHistory()} can correlate serve events back to their service.
 */
public final class WireMockStubRegistrar implements StubRegistrar {

    private final WireMockServer server;
    private final StatefulResponseTransformer stateful;
    private final ServiceRegistry registry = new ServiceRegistry();
    private String currentServiceId;

    public WireMockStubRegistrar(WireMockServer server, StatefulResponseTransformer stateful) {
        this.server = server;
        this.stateful = stateful;
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
        String actionPattern = "(?s)(.*&)?Action=" + Pattern.quote(actionName) + "(&.*)?";
        server.stubFor(post(anyUrl())
                .withRequestBody(matching(actionPattern))
                .withName(stubName(actionName))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_XML_UTF8)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.FORM_URL, actionName, responseTemplate,
                    CONTENT_TYPE_XML_UTF8, HttpURLConnection.HTTP_OK, null));
        }
    }

    @Override
    public void registerJsonTargetStub(String target, String responseTemplate) {
        server.stubFor(post(anyUrl())
                .withHeader(HEADER_AMZ_TARGET, equalTo(target))
                .withName(stubName(target))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_AMZ_JSON_1_1)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.JSON_TARGET, target, responseTemplate,
                    CONTENT_TYPE_AMZ_JSON_1_1, HttpURLConnection.HTTP_OK, null));
        }
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate) {
        String matchKey = method.name() + " " + pathPattern;
        server.stubFor(request(method.name(), urlMatching(pathPattern))
                .withName(stubName(matchKey))
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withBody(responseTemplate)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    StubProtocol.REST, matchKey, responseTemplate,
                    "application/octet-stream", HttpURLConnection.HTTP_OK, null));
        }
    }

    @Override
    public void registerXmlFormStub(String actionName, StubHandler handler) {
        String actionPattern = "(?s)(.*&)?Action=" + Pattern.quote(actionName) + "(&.*)?";
        registerHandlerStub(StubProtocol.FORM_URL, actionName,
                post(anyUrl()).withRequestBody(matching(actionPattern)),
                CONTENT_TYPE_XML_UTF8, handler);
    }

    @Override
    public void registerJsonTargetStub(String target, StubHandler handler) {
        registerHandlerStub(StubProtocol.JSON_TARGET, target,
                post(anyUrl()).withHeader(HEADER_AMZ_TARGET, equalTo(target)),
                CONTENT_TYPE_AMZ_JSON_1_1, handler);
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, StubHandler handler) {
        registerHandlerStub(StubProtocol.REST, method.name() + " " + pathPattern,
                request(method.name(), urlMatching(pathPattern)),
                "application/octet-stream", handler);
    }

    /**
     * Shared registration for all three stateful protocols: names the stub, registers the handler
     * under that key, and points the stub at the {@link StatefulResponseTransformer}. The base
     * response is empty — the transformer replaces its status, content type, and body at request
     * time. {@code contentType} is recorded only for fault stubs (the live response sets its own).
     */
    private void registerHandlerStub(StubProtocol protocol, String matchKey, MappingBuilder matcher,
                                     String contentType, StubHandler handler) {
        String key = stubName(matchKey);
        stateful.register(key, handler);
        server.stubFor(matcher
                .withName(key)
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withTransformers(StatefulResponseTransformer.NAME)
                        .withTransformerParameter(StatefulResponseTransformer.HANDLER_KEY_PARAM, key)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(
                    protocol, matchKey, "", contentType, HttpURLConnection.HTTP_OK, key));
        }
    }

    /** Returns a live snapshot of all modules and their registered stubs. */
    public List<ModuleStatus> moduleStatuses() {
        return registry.allServiceIds().stream()
                .sorted()
                .map(id -> new ModuleStatus(
                        id,
                        registry.getStubs(id).stream()
                                .map(r -> new StubInfo(r.protocol().name(), r.matchKey()))
                                .toList()))
                .toList();
    }

    private String stubName(String matchKey) {
        return currentServiceId != null
                ? "cloudmock:" + currentServiceId + ":" + matchKey
                : "cloudmock:unknown:" + matchKey;
    }
}

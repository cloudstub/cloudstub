package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import io.cloudmock.core.restapi.ModuleStatus;
import io.cloudmock.core.restapi.StubInfo;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubHandler;
import io.cloudmock.core.spi.StubRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * service ID so that {@link CloudMockResponseTransformer} can look up a matched stub's protocol
 * when decorating its response with a fault.
 *
 * <p>Stubs are named {@code cloudmock:<serviceId>:<matchKey>} so that
 * {@code CloudMock.requestHistory()} can correlate serve events back to their service, and so the
 * transformer can recover the service ID and match key of a matched stub from its name.
 */
public final class WireMockStubRegistrar implements StubRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WireMockStubRegistrar.class);

    private final WireMockServer server;
    private final CloudMockResponseTransformer transformer;
    private final ServiceRegistry registry;
    private String currentServiceId;

    public WireMockStubRegistrar(WireMockServer server, CloudMockResponseTransformer transformer,
                                 ServiceRegistry registry) {
        this.server = server;
        this.transformer = transformer;
        this.registry = registry;
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
            registry.record(currentServiceId, new StubRecord(StubProtocol.FORM_URL, actionName));
            log.debug("Stub registered: {} FORM_URL Action={}", currentServiceId, actionName);
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
            registry.record(currentServiceId, new StubRecord(StubProtocol.JSON_TARGET, target));
            log.debug("Stub registered: {} JSON_TARGET Target={}", currentServiceId, target);
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
            registry.record(currentServiceId, new StubRecord(StubProtocol.REST, matchKey));
            log.debug("Stub registered: {} REST {}", currentServiceId, matchKey);
        }
    }

    @Override
    public void registerXmlFormStub(String actionName, StubHandler handler) {
        String actionPattern = "(?s)(.*&)?Action=" + Pattern.quote(actionName) + "(&.*)?";
        registerHandlerStub(StubProtocol.FORM_URL, actionName,
                post(anyUrl()).withRequestBody(matching(actionPattern)), handler);
    }

    @Override
    public void registerJsonTargetStub(String target, StubHandler handler) {
        registerHandlerStub(StubProtocol.JSON_TARGET, target,
                post(anyUrl()).withHeader(HEADER_AMZ_TARGET, equalTo(target)), handler);
    }

    @Override
    public void registerRestStub(HttpMethod method, String pathPattern, StubHandler handler) {
        registerHandlerStub(StubProtocol.REST, method.name() + " " + pathPattern,
                request(method.name(), urlMatching(pathPattern)), handler);
    }

    /**
     * Shared registration for all three stateful protocols: names the stub and registers the handler
     * under that key. The base response is an empty {@code 200} — the global
     * {@link CloudMockResponseTransformer} runs the handler at request time and replaces the status,
     * content type, and body.
     */
    private void registerHandlerStub(StubProtocol protocol, String matchKey, MappingBuilder matcher,
                                     StubHandler handler) {
        String key = stubName(matchKey);
        transformer.register(key, handler);
        server.stubFor(matcher
                .withName(key)
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK)));
        if (currentServiceId != null) {
            registry.record(currentServiceId, new StubRecord(protocol, matchKey));
            log.debug("Stub registered: {} {} (stateful) {}", currentServiceId, protocol, matchKey);
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

package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.cloudmock.core.internal.HttpConstants.*;

/**
 * Creates and removes high-priority WireMock fault stubs for a named service.
 *
 * <p>Fault stubs run at WireMock priority 1 (lower number = higher priority), so they
 * override the normal service stubs registered at the default priority of 5.
 * Removing fault stubs restores normal behaviour without touching the original stubs.
 */
public class FaultEngine {

    private static final int FAULT_PRIORITY = 1;
    static final int TIMEOUT_DELAY_MS = 30_000;

    private static final String THROTTLE_JSON_BODY =
            "{\"__type\":\"ThrottlingException\",\"message\":\"Rate exceeded\"}";
    private static final String THROTTLE_XML_BODY =
            "<ErrorResponse><Error><Code>ThrottlingException</Code>" +
            "<Message>Rate exceeded</Message></Error></ErrorResponse>";

    private final WireMockServer server;
    private final ServiceRegistry registry;
    private final Map<String, List<StubMapping>> activeFaults = new HashMap<>();

    FaultEngine(WireMockServer server, ServiceRegistry registry) {
        this.server = server;
        this.registry = registry;
    }

    public void injectThrottle(String serviceId) {
        List<StubMapping> stubs = new ArrayList<>();
        for (StubRecord record : registry.getStubs(serviceId)) {
            stubs.add(server.stubFor(throttleMapping(record)));
        }
        activeFaults.computeIfAbsent(serviceId, k -> new ArrayList<>()).addAll(stubs);
    }

    public void injectTimeout(String serviceId) {
        List<StubMapping> stubs = new ArrayList<>();
        for (StubRecord record : registry.getStubs(serviceId)) {
            stubs.add(server.stubFor(timeoutMapping(record)));
        }
        activeFaults.computeIfAbsent(serviceId, k -> new ArrayList<>()).addAll(stubs);
    }

    public void injectBrownout(String serviceId, double rate) {
        if (rate <= 0.0) {
            return;
        }
        List<StubMapping> stubs = new ArrayList<>();
        for (StubRecord record : registry.getStubs(serviceId)) {
            MappingBuilder mapping = rate >= 1.0
                    ? brownoutAlwaysMapping(record)
                    : brownoutProbabilisticMapping(record, rate);
            stubs.add(server.stubFor(mapping));
        }
        activeFaults.computeIfAbsent(serviceId, k -> new ArrayList<>()).addAll(stubs);
    }

    public void clearFaults(String serviceId) {
        List<StubMapping> stubs = activeFaults.remove(serviceId);
        if (stubs != null) {
            stubs.forEach(server::removeStubMapping);
        }
    }

    public void clearAllFaults() {
        new ArrayList<>(activeFaults.keySet()).forEach(this::clearFaults);
    }

    private MappingBuilder throttleMapping(StubRecord record) {
        String body = record.protocol() == StubProtocol.FORM_URL ? THROTTLE_XML_BODY : THROTTLE_JSON_BODY;
        String contentType = record.protocol() == StubProtocol.FORM_URL
                ? CONTENT_TYPE_XML_UTF8
                : CONTENT_TYPE_AMZ_JSON_1_1;
        return matcherFor(record)
                .atPriority(FAULT_PRIORITY)
                .willReturn(aResponse()
                        .withStatus(HttpURLConnection.HTTP_BAD_REQUEST)
                        .withHeader(HEADER_CONTENT_TYPE, contentType)
                        .withBody(body));
    }

    private MappingBuilder timeoutMapping(StubRecord record) {
        // A timeout fault only needs to delay; the AWS SDK aborts before reading the body. We
        // deliberately do NOT run a stateful handler here — the response is discarded, so executing
        // the handler would mutate the state store as a surprising side effect of a simulated timeout.
        return matcherFor(record)
                .atPriority(FAULT_PRIORITY)
                .willReturn(aResponse()
                        .withStatus(record.statusCode())
                        .withHeader(HEADER_CONTENT_TYPE, record.contentType())
                        .withBody(record.responseTemplate())
                        .withFixedDelay(TIMEOUT_DELAY_MS));
    }

    private MappingBuilder brownoutAlwaysMapping(StubRecord record) {
        return matcherFor(record)
                .atPriority(FAULT_PRIORITY)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
    }

    private MappingBuilder brownoutProbabilisticMapping(StubRecord record, double rate) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(record.statusCode())
                .withHeader(HEADER_CONTENT_TYPE, record.contentType())
                .withBody(record.responseTemplate())
                .withTransformerParameters(Parameters.one(BrownoutTransformer.RATE_PARAM, rate));
        if (record.handlerKey() != null) {
            // Stateful first so it builds the live body; brownout then decides pass-through vs reset.
            // Unlike timeout, a probabilistic brownout must run the handler: the requests that are
            // NOT reset have to return real data. A reset request that already wrote to the store
            // mirrors AWS's at-least-once delivery (the server processed it; the response was lost).
            response.withTransformers(StatefulResponseTransformer.NAME, BrownoutTransformer.NAME)
                    .withTransformerParameter(
                            StatefulResponseTransformer.HANDLER_KEY_PARAM, record.handlerKey());
        } else {
            response.withTransformers(BrownoutTransformer.NAME);
        }
        return matcherFor(record).atPriority(FAULT_PRIORITY).willReturn(response);
    }

    private MappingBuilder matcherFor(StubRecord record) {
        return switch (record.protocol()) {
            case FORM_URL -> post(anyUrl())
                    .withRequestBody(containing("Action=" + record.matchKey()));
            case JSON_TARGET -> post(anyUrl())
                    .withHeader(HEADER_AMZ_TARGET, equalTo(record.matchKey()));
            case REST -> {
                String[] parts = record.matchKey().split(" ", 2);
                yield request(parts[0], urlMatching(parts[1]));
            }
        };
    }
}

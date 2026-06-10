package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.cloudmock.core.spi.StateStore;
import io.cloudmock.core.spi.StubHandler;
import io.cloudmock.core.spi.StubResponse;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static io.cloudmock.core.internal.HttpConstants.*;

/**
 * The single global response transformer: it produces a matched stub's real response — running its
 * module-supplied {@link StubHandler} against the shared {@link StateStore} when the stub is
 * stateful — and then applies any active fault for the stub's service as a decoration over that
 * response.
 *
 * <p>Whether a fault runs the underlying handler depends on the fault type:
 * <ul>
 *   <li><b>Throttle</b> replaces the body with an error and <b>timeout</b> only delays it; the SDK
 *       discards the body in both cases, so the handler is <em>not</em> run.</li>
 *   <li>A full-rate <b>brownout</b> always resets the connection, so the handler is not run.</li>
 *   <li>A probabilistic <b>brownout</b> runs the handler, since requests that are not reset must
 *       return real data; a reset request that already wrote to the store leaves that write in place.</li>
 * </ul>
 * The handler runs at most once per request.
 */
public class CloudMockResponseTransformer implements ResponseTransformerV2 {

    public static final String NAME = "cloudmock-response";

    /** Prefix shared with {@link WireMockStubRegistrar} stub names: {@code cloudmock:<id>:<key>}. */
    static final String STUB_NAME_PREFIX = "cloudmock:";

    private static final int TIMEOUT_DELAY_MS = 30_000;

    private static final String THROTTLE_JSON_BODY =
            "{\"__type\":\"ThrottlingException\",\"message\":\"Rate exceeded\"}";
    private static final String THROTTLE_XML_BODY =
            "<ErrorResponse><Error><Code>ThrottlingException</Code>" +
            "<Message>Rate exceeded</Message></Error></ErrorResponse>";

    private final StateStore stateStore;
    private final ServiceRegistry registry;
    private final FaultEngine faultEngine;
    private final Map<String, StubHandler> handlers = new ConcurrentHashMap<>();

    public CloudMockResponseTransformer(StateStore stateStore, ServiceRegistry registry,
                                        FaultEngine faultEngine) {
        this.stateStore = stateStore;
        this.registry = registry;
        this.faultEngine = faultEngine;
    }

    /** Registers {@code handler} under {@code key} (the stub name); looked up at request time. */
    void register(String key, StubHandler handler) {
        handlers.put(key, handler);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        StubMapping mapping = serveEvent.getStubMapping();
        String name = mapping == null ? null : mapping.getName();
        if (name == null || !name.startsWith(STUB_NAME_PREFIX)) {
            return response;   // unmatched request or non-CloudMock stub: nothing to do
        }
        String[] parts = name.substring(STUB_NAME_PREFIX.length()).split(":", 2);
        if (parts.length < 2) {
            return response;
        }
        String serviceId = parts[0];

        FaultEngine.Fault fault = faultEngine.faultFor(serviceId);
        if (fault == null) {
            return runHandler(name, response, serveEvent);
        }
        return switch (fault.type()) {
            case THROTTLE -> throttle(registry.find(serviceId, parts[1]), response);
            case TIMEOUT -> Response.Builder.like(response).but()
                    .incrementInitialDelay(TIMEOUT_DELAY_MS).build();
            case BROWNOUT -> brownout(fault.rate(), name, response, serveEvent);
        };
    }

    /**
     * Runs the stub's handler (for stateful stubs) and renders its {@link StubResponse}; for
     * template stubs there is no handler, so the already-rendered {@code response} is returned
     * unchanged.
     */
    private Response runHandler(String name, Response response, ServeEvent serveEvent) {
        StubHandler handler = handlers.get(name);
        if (handler == null) {
            return response;
        }
        StubResponse result = handler.handle(new WireMockStubRequest(serveEvent.getRequest()), stateStore);
        HttpHeaders headers = new HttpHeaders(new HttpHeader(HEADER_CONTENT_TYPE, result.contentType()));
        for (Map.Entry<String, String> header : result.headers().entrySet()) {
            headers = headers.plus(new HttpHeader(header.getKey(), header.getValue()));
        }
        return Response.Builder.like(response)
                .but()
                .status(result.status())
                .headers(headers)
                .body(result.body())
                .build();
    }

    /** Replaces the response with a protocol-appropriate ThrottlingException; never runs the handler. */
    private Response throttle(StubRecord record, Response response) {
        boolean xml = record != null && record.protocol() == StubProtocol.FORM_URL;
        String contentType = xml ? CONTENT_TYPE_XML_UTF8 : CONTENT_TYPE_AMZ_JSON_1_1;
        return Response.Builder.like(response)
                .but()
                .status(HttpURLConnection.HTTP_BAD_REQUEST)
                .headers(new HttpHeaders(new HttpHeader(HEADER_CONTENT_TYPE, contentType)))
                .body(xml ? THROTTLE_XML_BODY : THROTTLE_JSON_BODY)
                .build();
    }

    private Response brownout(double rate, String name, Response response, ServeEvent serveEvent) {
        if (rate >= 1.0) {
            return connectionReset();   // always resets: no point running the handler
        }
        Response normal = runHandler(name, response, serveEvent);
        return ThreadLocalRandom.current().nextDouble() < rate ? connectionReset() : normal;
    }

    private Response connectionReset() {
        return Response.response()
                .configured(true)
                .fault(Fault.CONNECTION_RESET_BY_PEER)
                .build();
    }
}

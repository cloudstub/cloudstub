package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.cloudmock.core.spi.StateStore;
import io.cloudmock.core.spi.StubHandler;
import io.cloudmock.core.spi.StubResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.cloudmock.core.internal.HttpConstants.HEADER_CONTENT_TYPE;

/**
 * WireMock extension that runs a module-supplied {@link StubHandler} at request time, giving the
 * module access to the shared {@link StateStore}. This is the bridge that turns CloudMock from a
 * stateless template server into a stateful one — without exposing any WireMock type to modules.
 *
 * <p>Handlers are looked up by a {@code handlerKey} transformer parameter rather than by stub name,
 * so a fault stub (timeout, brownout) created by {@link FaultEngine} can carry the same key and
 * still run the real handler after applying its fault. Applied only to stubs that opt in with
 * {@code .withTransformers(NAME)}, never globally.
 *
 * <p>Patterned on {@link BrownoutTransformer}: a single shared extension instance reads its
 * per-stub parameter from the response definition.
 */
public class StatefulResponseTransformer implements ResponseTransformerV2 {

    public static final String NAME = "cloudmock-stateful";
    static final String HANDLER_KEY_PARAM = "handlerKey";

    private final StateStore stateStore;
    private final Map<String, StubHandler> handlers = new ConcurrentHashMap<>();

    public StatefulResponseTransformer(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    /** Registers {@code handler} under {@code key}; the matching stub carries the key as a parameter. */
    void register(String key, StubHandler handler) {
        handlers.put(key, handler);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        Object key = serveEvent.getResponseDefinition()
                .getTransformerParameters().get(HANDLER_KEY_PARAM);
        if (key == null) {
            return response;
        }
        StubHandler handler = handlers.get(key.toString());
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
}

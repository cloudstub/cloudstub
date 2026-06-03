package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * WireMock extension that turns a normal response into a {@link Fault#CONNECTION_RESET_BY_PEER}
 * with probability {@code rate} (0.0 = never fault, 1.0 = always fault).
 *
 * <p>Applied only to stubs that explicitly opt in with
 * {@code .withTransformers(BrownoutTransformer.NAME)}, never globally.
 */
public class BrownoutTransformer implements ResponseTransformerV2 {

    public static final String NAME = "cloudmock-brownout";
    static final String RATE_PARAM = "rate";

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
        double rate = ((Number) serveEvent.getResponseDefinition()
                .getTransformerParameters().get(RATE_PARAM)).doubleValue();
        if (ThreadLocalRandom.current().nextDouble() < rate) {
            return Response.response()
                    .configured(true)
                    .fault(Fault.CONNECTION_RESET_BY_PEER)
                    .build();
        }
        return response;
    }
}

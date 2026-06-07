package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubRequest;

/**
 * Adapts a WireMock {@link Request} to the public {@link StubRequest} SPI view, so that
 * {@link io.cloudmock.core.spi.StubHandler}s never see a WireMock type.
 */
final class WireMockStubRequest implements StubRequest {

    private final Request request;

    WireMockStubRequest(Request request) {
        this.request = request;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.valueOf(request.getMethod().value());
    }

    @Override
    public String path() {
        String url = request.getUrl();
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    @Override
    public String body() {
        String body = request.getBodyAsString();
        return body != null ? body : "";
    }

    @Override
    public String header(String name) {
        return request.getHeader(name);
    }

    @Override
    public String queryParam(String name) {
        QueryParameter param = request.queryParameter(name);
        return param != null && param.isPresent() ? param.firstValue() : null;
    }
}

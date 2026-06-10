package io.cloudmock.core.internal;

import io.cloudmock.core.spi.StubResponse;

final class HttpConstants {

    private HttpConstants() {}

    static final String HEADER_CONTENT_TYPE = "Content-Type";
    static final String HEADER_AMZ_TARGET   = "X-Amz-Target";
    static final String CONTENT_TYPE_XML_UTF8     = StubResponse.CONTENT_TYPE_XML;
    static final String CONTENT_TYPE_AMZ_JSON_1_1 = StubResponse.CONTENT_TYPE_JSON;
}

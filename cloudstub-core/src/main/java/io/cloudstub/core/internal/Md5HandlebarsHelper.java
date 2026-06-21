package io.cloudstub.core.internal;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import io.cloudstub.core.spi.Digest;
import java.util.Map;

/**
 * WireMock extension that registers a {@code {{md5 value}}} Handlebars helper. Computes the MD5 hex
 * digest of any string at request time, so stubs always return a checksum that matches the actual
 * body rather than a hardcoded value.
 */
public class Md5HandlebarsHelper implements TemplateHelperProviderExtension, Helper<Object> {

    public static final String HELPER_NAME = "md5";

    @Override
    public String getName() {
        return "cloudstub-md5-helper";
    }

    @Override
    public Map<String, Helper<?>> provideTemplateHelpers() {
        return Map.of(HELPER_NAME, this);
    }

    @Override
    public Object apply(Object context, Options options) {
        if (context == null) {
            return "";
        }
        return Digest.md5Hex(context.toString());
    }
}

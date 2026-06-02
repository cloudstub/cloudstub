package io.cloudmock.core.internal;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * WireMock extension that registers a {@code {{md5 value}}} Handlebars helper.
 * Computes the MD5 hex digest of any string at request time, so stubs always
 * return a checksum that matches the actual body rather than a hardcoded value.
 */
public class Md5HandlebarsHelper implements TemplateHelperProviderExtension, Helper<Object> {

    public static final String HELPER_NAME = "md5";

    @Override
    public String getName() {
        return "cloudmock-md5-helper";
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
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(context.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

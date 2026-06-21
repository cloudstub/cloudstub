package io.cloudstub.s3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * S3 path helpers: bucket and object-key extraction from a path-style request path. Dependency-free
 * (JDK only).
 */
final class S3Helpers {

    private S3Helpers() {}

    /** The bucket name (first path segment) from a path-style request path, e.g. {@code /b/k}. */
    static String bucket(String path) {
        int start = path.startsWith("/") ? 1 : 0;
        int slash = path.indexOf('/', start);
        return slash < 0 ? path.substring(start) : path.substring(start, slash);
    }

    /**
     * The object key (everything after the bucket segment) from a path-style request path, or an
     * empty string for a bucket-level path. The key is percent-decoded; {@code '/'} is a key
     * separator (not encoded by S3) and is preserved.
     */
    static String objectKey(String path) {
        int start = path.startsWith("/") ? 1 : 0;
        int slash = path.indexOf('/', start);
        return slash < 0 ? "" : percentDecode(path.substring(slash + 1));
    }

    /**
     * Decodes {@code %XX} escapes in a URL path segment. Unlike {@link java.net.URLDecoder}, a
     * literal {@code '+'} is left as-is: in an S3 path a {@code '+'} is a literal plus (a space is
     * sent as {@code %20}), so form-style {@code +}→space decoding would corrupt keys.
     */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}

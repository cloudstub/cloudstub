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
     * Whether the request body is {@code aws-chunked} framed. True when {@code
     * x-amz-content-sha256} is a {@code STREAMING-*} value or {@code Content-Encoding} is {@code
     * aws-chunked}.
     */
    static boolean isAwsChunked(String contentSha256, String contentEncoding) {
        if (contentSha256 != null && contentSha256.startsWith("STREAMING-")) {
            return true;
        }
        return contentEncoding != null && contentEncoding.contains("aws-chunked");
    }

    /**
     * Strips {@code aws-chunked} framing, returning the decoded object content. Each chunk is a hex
     * byte-count with an optional {@code ;chunk-signature=…} extension, a CRLF, that many data
     * bytes, and a trailing CRLF; a zero-size chunk ends the body. An unframed body is returned
     * unchanged. Sizes are byte counts, so decoding runs over the UTF-8 bytes.
     */
    static String decodeAwsChunked(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        int pos = 0;
        boolean decodedAny = false;
        while (pos < bytes.length) {
            int lineEnd = indexOfCrlf(bytes, pos);
            if (lineEnd < 0) {
                break;
            }
            String header = new String(bytes, pos, lineEnd - pos, StandardCharsets.US_ASCII);
            int semicolon = header.indexOf(';');
            String sizeHex = (semicolon < 0 ? header : header.substring(0, semicolon)).trim();
            int size;
            try {
                size = Integer.parseInt(sizeHex, 16);
            } catch (NumberFormatException e) {
                return decodedAny ? out.toString(StandardCharsets.UTF_8) : body;
            }
            if (size == 0) {
                decodedAny = true;
                break;
            }
            int dataStart = lineEnd + 2;
            int available = Math.min(size, bytes.length - dataStart);
            if (available <= 0) {
                break;
            }
            out.write(bytes, dataStart, available);
            decodedAny = true;
            pos = dataStart + available + 2;
        }
        return decodedAny ? out.toString(StandardCharsets.UTF_8) : body;
    }

    /** Index of the next {@code CRLF} in {@code bytes} at or after {@code from}, or {@code -1}. */
    private static int indexOfCrlf(byte[] bytes, int from) {
        for (int i = from; i + 1 < bytes.length; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
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

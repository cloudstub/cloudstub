package io.cloudmock.sqs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON helpers for the SQS module. Kept deliberately small and dependency-free so the
 * module needs only {@code cloudmock-core} and the JDK — no jackson — matching the
 * {@link io.cloudmock.core.spi.StubHandler} constraint.
 *
 * <p>The AWS SDK sends well-formed flat JSON for SQS operations, so a targeted extractor is
 * sufficient; this is not a general-purpose parser.
 */
final class SqsJson {

    private SqsJson() {}

    /**
     * Returns the unescaped value of a JSON string field, or {@code null} if absent.
     * Handles standard JSON escapes inside the value.
     */
    static String stringField(String body, String field) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        return m.find() ? unescape(m.group(1)) : null;
    }

    /** Returns the value of a JSON numeric field, or {@code defaultValue} if absent. */
    static int intField(String body, String field, int defaultValue) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
    }

    /** Escapes a string for embedding as a JSON string value (without surrounding quotes). */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'u'  -> {
                        sb.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Lowercase hex MD5 digest of {@code value}, used for SQS message checksums. */
    static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /** Extracts the queue name (last path segment) from an SQS queue URL. */
    static String queueName(String queueUrl) {
        if (queueUrl == null) {
            return null;
        }
        int slash = queueUrl.lastIndexOf('/');
        return slash >= 0 ? queueUrl.substring(slash + 1) : queueUrl;
    }
}

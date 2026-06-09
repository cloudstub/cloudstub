package io.cloudmock.sqs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON helpers for the SQS module. Kept deliberately small and dependency-free so the
 * module needs only {@code cloudmock-core} and the JDK — no jackson — matching the
 * {@link io.cloudmock.core.spi.StubHandler} constraint. Shared by the stub handlers and the
 * REST/CLI API service so message checksums stay consistent across both surfaces.
 *
 * <p>The AWS SDK sends well-formed flat JSON for SQS operations, so a targeted extractor is
 * sufficient; this is not a general-purpose parser.
 */
final class SqsJson {

    private SqsJson() {}

    /** Compiled string-field patterns are cached: the same handful of field names recur every request. */
    private static final ConcurrentHashMap<String, Pattern> STRING_PATTERNS = new ConcurrentHashMap<>();

    private static final Pattern MAX_MESSAGES =
            Pattern.compile("\"MaxNumberOfMessages\"\\s*:\\s*(\\d+)");

    /**
     * Returns the unescaped value of a JSON string field, or {@code null} if absent.
     * Handles standard JSON escapes inside the value.
     */
    static String stringField(String body, String field) {
        Pattern p = STRING_PATTERNS.computeIfAbsent(field, f ->
                Pattern.compile("\"" + Pattern.quote(f) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""));
        Matcher m = p.matcher(body);
        return m.find() ? unescape(m.group(1)) : null;
    }

    /**
     * Returns the {@code MaxNumberOfMessages} value from a ReceiveMessage body, defaulting to 1
     * (the AWS default) when absent or unparseable. Values are clamped to a positive int.
     */
    static int maxNumberOfMessages(String body) {
        Matcher m = MAX_MESSAGES.matcher(body);
        if (!m.find()) {
            return 1;
        }
        try {
            long value = Long.parseLong(m.group(1));
            return value < 1 ? 1 : (int) Math.min(value, Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            return 1;
        }
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
                    case 'u'  -> i = appendUnicodeEscape(sb, value, i);
                    default   -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Appends the character for a {@code \\uXXXX} escape starting just after the {@code u} at index
     * {@code uIndex}, returning the new scan index. A truncated or non-hex sequence is treated as a
     * literal {@code u} (no exception) so a malformed body cannot crash the handler.
     */
    private static int appendUnicodeEscape(StringBuilder sb, String value, int uIndex) {
        if (uIndex + 5 <= value.length()) {
            String hex = value.substring(uIndex + 1, uIndex + 5);
            try {
                sb.append((char) Integer.parseInt(hex, 16));
                return uIndex + 4;
            } catch (NumberFormatException ignored) {
                // fall through: not a valid unicode escape, keep the 'u' literal
            }
        }
        sb.append('u');
        return uIndex;
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

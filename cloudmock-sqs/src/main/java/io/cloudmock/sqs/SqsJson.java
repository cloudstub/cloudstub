package io.cloudmock.sqs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SQS-specific helpers for building responses and computing checksums. Request-body field extraction
 * now lives in core ({@link io.cloudmock.core.spi.StubRequest#jsonField}), so this class holds only
 * what is genuinely SQS-shaped: JSON string escaping for hand-built response bodies, the message MD5
 * checksum, and queue-name extraction from a queue URL. Dependency-free (JDK only).
 */
final class SqsJson {

    private SqsJson() {}

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

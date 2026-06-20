package io.cloudstub.sqs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SQS-specific helpers. Request-body field extraction lives in core ({@link
 * io.cloudstub.core.spi.StubRequest#jsonField}) and response JSON is serialised by the engine via
 * {@link io.cloudstub.core.spi.StubResponse#json(java.util.Map)}, so this class holds only what is
 * genuinely SQS-shaped: the message MD5 checksum and queue-name extraction from a queue URL.
 * Dependency-free (JDK only).
 */
final class SqsHelpers {

    private SqsHelpers() {}

    /** Lowercase hex MD5 digest of {@code value}, used for SQS message checksums. */
    static String md5(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
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

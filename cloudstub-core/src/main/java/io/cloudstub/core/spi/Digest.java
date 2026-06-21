package io.cloudstub.core.spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Checksum helpers for service modules (SQS message checksums, S3 ETags). JDK-only; exposes no
 * networking or serialisation type.
 */
public final class Digest {

    private Digest() {}

    /** Lowercase hex MD5 digest of {@code value}. */
    public static String md5Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value);
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

    /** Lowercase hex MD5 digest of {@code value}'s UTF-8 bytes. */
    public static String md5Hex(String value) {
        return md5Hex(value.getBytes(StandardCharsets.UTF_8));
    }
}

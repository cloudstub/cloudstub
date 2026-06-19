package io.cloudstub.secretsmanager;

/**
 * Secrets Manager helpers for building hand-written JSON response bodies and resolving a {@code
 * SecretId} to a secret name. Request-body field extraction lives in core ({@link
 * io.cloudstub.core.spi.StubRequest#jsonField}), so this class holds only what is Secrets
 * Manager-shaped. Dependency-free (JDK only).
 */
final class SecretsManagerJson {

    private SecretsManagerJson() {}

    static final String ARN_PREFIX = "arn:aws:secretsmanager:us-east-1:000000000000:secret:";
    private static final String ARN_INFIX = ":secret:";

    /** The ARN CloudStub assigns to a secret of the given name. */
    static String arn(String name) {
        return ARN_PREFIX + name;
    }

    /**
     * Resolves a {@code SecretId} — which AWS accepts as either a secret name or a full ARN — to
     * the secret name used as the store key. An ARN is reduced to the segment after {@code
     * :secret:}; any other value is treated as the name itself.
     */
    static String resolveName(String secretId) {
        if (secretId == null) {
            return null;
        }
        int infix = secretId.indexOf(ARN_INFIX);
        return infix >= 0 ? secretId.substring(infix + ARN_INFIX.length()) : secretId;
    }

    /** Escapes a string for embedding as a JSON string value (without surrounding quotes). */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
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
}

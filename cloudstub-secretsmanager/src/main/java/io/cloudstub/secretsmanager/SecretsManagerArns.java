package io.cloudstub.secretsmanager;

/**
 * Secrets Manager ARN helpers: builds the ARN CloudStub assigns to a secret and resolves a {@code
 * SecretId} (a name or a full ARN) back to the secret name used as the store key. Response-body
 * JSON is built by the engine via {@link io.cloudstub.core.spi.StubResponse#json(java.util.Map)}
 * and request fields are read via {@link io.cloudstub.core.spi.StubRequest#jsonField}, so this
 * class holds only what is Secrets Manager-shaped. Dependency-free (JDK only).
 */
final class SecretsManagerArns {

    private SecretsManagerArns() {}

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
}

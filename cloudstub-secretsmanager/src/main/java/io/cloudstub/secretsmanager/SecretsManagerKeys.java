package io.cloudstub.secretsmanager;

/**
 * The Secrets Manager state-store key scheme, defined once so the AWS-protocol surface ({@link
 * CloudStubSecretsManagerService}) and the REST/CLI surface ({@link
 * CloudStubSecretsManagerApiService}) address exactly the same data and cannot drift.
 *
 * <p>Each secret is stored as a single entry under the {@code secretsmanager/secrets/} prefix,
 * keyed by the secret name:
 *
 * <ul>
 *   <li>{@code secretsmanager/secrets/{name}} → the secret record (a map of its fields)
 *   <li>{@code secretsmanager/tags/{name}} → the secret's tags (a map of tag key → value)
 * </ul>
 *
 * <p>Tags are held in a separate entry rather than inside the secret record so the secret record
 * stays a flat {@code Map<String, String>} and {@code list(SECRETS_PREFIX)} continues to enumerate
 * exactly one entry per secret — which keeps listing correct even though AWS secret names may
 * themselves contain {@code '/'}.
 */
final class SecretsManagerKeys {

    private SecretsManagerKeys() {}

    static final String SECRETS_PREFIX = "secretsmanager/secrets/";
    static final String TAGS_PREFIX = "secretsmanager/tags/";

    static String secretKey(String name) {
        return SECRETS_PREFIX + name;
    }

    static String tagsKey(String name) {
        return TAGS_PREFIX + name;
    }

    /** The secret name carried by a full secret key. */
    static String nameOf(String key) {
        return key.substring(SECRETS_PREFIX.length());
    }
}

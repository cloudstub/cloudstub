package io.cloudstub.ssm;

/**
 * The SSM state-store key scheme, defined once so the AWS-protocol surface ({@link
 * CloudStubSSMService}) and the REST/CLI surface ({@link CloudStubSSMApiService}) address exactly
 * the same data and cannot drift. Keys live under the {@code ssm/} prefix:
 *
 * <ul>
 *   <li>{@code ssm/parameters/{name}} → the stored parameter (name, value, type, version, ...)
 *   <li>{@code ssm/tags/{name}} → the parameter's tags, as a {@code Map<String, String>}
 * </ul>
 *
 * <p>Parameter names are hierarchical and may contain {@code '/'} (e.g. {@code /prod/db/password}),
 * which becomes part of the key verbatim. A parameter has no sub-resources of its own, so every key
 * under {@link #PARAMETERS_PREFIX} addresses one parameter; tags are held under the separate {@link
 * #TAGS_PREFIX} so a listing of parameters never returns a tag key.
 */
final class SsmKeys {

    private SsmKeys() {}

    static final String PARAMETERS_PREFIX = "ssm/parameters/";
    static final String TAGS_PREFIX = "ssm/tags/";

    static String parameterKey(String name) {
        return PARAMETERS_PREFIX + name;
    }

    static String tagsKey(String name) {
        return TAGS_PREFIX + name;
    }

    /** The parameter name for a key under {@link #PARAMETERS_PREFIX}. */
    static String nameFromKey(String key) {
        return key.substring(PARAMETERS_PREFIX.length());
    }
}

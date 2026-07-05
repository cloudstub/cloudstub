package io.cloudstub.lambda;

/**
 * The Lambda state-store key scheme, defined once so the AWS-protocol surface ({@link
 * CloudStubLambdaService}) and the REST/CLI surface ({@link CloudStubLambdaApiService}) address
 * exactly the same data and cannot drift. Keys live under the {@code lambda/} prefix:
 *
 * <ul>
 *   <li>{@code lambda/functions/{name}} → the function configuration
 *   <li>{@code lambda/tags/{name}} → the function's tag set
 * </ul>
 */
final class LambdaKeys {

    private LambdaKeys() {}

    static final String FUNCTIONS_PREFIX = "lambda/functions/";
    static final String TAGS_PREFIX = "lambda/tags/";

    static String functionKey(String name) {
        return FUNCTIONS_PREFIX + name;
    }

    static String tagsKey(String name) {
        return TAGS_PREFIX + name;
    }
}

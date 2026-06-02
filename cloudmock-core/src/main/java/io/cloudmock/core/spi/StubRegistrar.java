package io.cloudmock.core.spi;

/**
 * Facade through which service modules register HTTP stubs without any direct
 * dependency on the underlying networking driver (WireMock). The implementation
 * lives in {@code cloudmock-core}'s internal package and is never exposed.
 *
 * <p>Three routing protocols are supported, one per AWS request style:
 * <ul>
 *   <li><b>XML / Form URL</b> (SQS, SNS) — matched on the {@code Action} form body parameter.</li>
 *   <li><b>JSON / X-Amz-Target</b> (Secrets Manager, DynamoDB) — matched on the {@code X-Amz-Target} header.</li>
 *   <li><b>REST path</b> (S3) — matched on HTTP method and path regex.</li>
 * </ul>
 *
 * <p>Response templates are Handlebars strings. The networking driver evaluates them at
 * request time, so templates may echo back fields from the incoming request (e.g. queue
 * names, message bodies) without any Java code in the module.
 *
 * <p>This interface is frozen as of Phase 1 and covered by CloudMock's public API stability
 * guarantee. Breaking changes require a major version bump of {@code cloudmock-core}.
 */
public interface StubRegistrar {

    /**
     * Registers a stub for XML/Form URL services (e.g. SQS, SNS).
     *
     * <p>Matches any {@code POST} request whose {@code application/x-www-form-urlencoded}
     * body contains {@code Action=<actionName>}.
     *
     * @param actionName       the value of the {@code Action} form parameter, e.g. {@code "SendMessage"}
     * @param responseTemplate Handlebars template for the HTTP response body
     */
    void registerXmlFormStub(String actionName, String responseTemplate);

    /**
     * Registers a stub for JSON / X-Amz-Target services (e.g. Secrets Manager, DynamoDB).
     *
     * <p>Matches any {@code POST} request whose {@code X-Amz-Target} header equals
     * {@code target}.
     *
     * @param target           full target header value, e.g. {@code "secretsmanager.GetSecretValue"}
     * @param responseTemplate Handlebars template for the HTTP response body
     */
    void registerJsonTargetStub(String target, String responseTemplate);

    /**
     * Registers a stub for REST path-based services (e.g. S3).
     *
     * <p>Matches requests whose HTTP method equals {@code method} and whose path matches
     * {@code pathPattern} as a regular expression.
     *
     * @param method           HTTP method to match
     * @param pathPattern      regular expression matched against the request path
     * @param responseTemplate Handlebars template for the HTTP response body
     */
    void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate);
}

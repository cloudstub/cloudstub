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
 * <p>Each protocol comes in two flavours:
 * <ul>
 *   <li><b>Template stubs</b> take a Handlebars response string. The networking driver evaluates it
 *       at request time, echoing back request fields (queue names, message bodies) with no Java
 *       code in the module. These are stateless — every call gets the same templated answer.</li>
 *   <li><b>Stateful stubs</b> take a {@link StubHandler} instead. The handler runs module Java code
 *       on each matching request with access to the shared {@link StateStore}, so what a user sends
 *       in one call can be returned by a later call.</li>
 * </ul>
 *
 * <p>Only the additive {@link StubHandler} overloads were added after the Phase 1 freeze; the
 * template methods are unchanged and covered by CloudMock's public API stability guarantee.
 * Breaking changes require a major version bump of {@code cloudmock-core}.
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

    /**
     * Registers a <em>stateful</em> stub for JSON / X-Amz-Target services (e.g. SQS, DynamoDB).
     *
     * <p>Matches like {@link #registerJsonTargetStub(String, String)}, but invokes {@code handler}
     * on each request instead of rendering a static template, giving the module access to the
     * shared {@link StateStore}.
     *
     * @param target  full target header value, e.g. {@code "AmazonSQS.SendMessage"}
     * @param handler request-time handler with access to the state store
     */
    void registerJsonTargetStub(String target, StubHandler handler);

    /**
     * Registers a <em>stateful</em> stub for XML/Form URL services (e.g. SQS, SNS).
     *
     * <p>Matches like {@link #registerXmlFormStub(String, String)}, but invokes {@code handler} on
     * each request instead of rendering a static template.
     *
     * @param actionName the value of the {@code Action} form parameter, e.g. {@code "SendMessage"}
     * @param handler    request-time handler with access to the state store
     */
    void registerXmlFormStub(String actionName, StubHandler handler);

    /**
     * Registers a <em>stateful</em> stub for REST path-based services (e.g. S3).
     *
     * <p>Matches like {@link #registerRestStub(HttpMethod, String, String)}, but invokes
     * {@code handler} on each request instead of rendering a static template.
     *
     * @param method      HTTP method to match
     * @param pathPattern regular expression matched against the request path
     * @param handler     request-time handler with access to the state store
     */
    void registerRestStub(HttpMethod method, String pathPattern, StubHandler handler);
}

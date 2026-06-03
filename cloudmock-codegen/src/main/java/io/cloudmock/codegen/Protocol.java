package io.cloudmock.codegen;

/**
 * The AWS request/response protocol used by a service, mapped to the corresponding
 * {@code StubRegistrar} method and response body format.
 */
enum Protocol {
    /** {@code @aws.protocols#awsJson1_0} or {@code #awsJson1_1} → {@code registerJsonTargetStub}, JSON body. */
    JSON_TARGET,
    /** {@code @aws.protocols#query} → {@code registerXmlFormStub}, XML body. */
    FORM_URL,
    /** {@code @aws.protocols#restJson1} → {@code registerRestStub}, JSON body. */
    REST_JSON,
    /** {@code @aws.protocols#restXml} → {@code registerRestStub}, XML body. */
    REST_XML;

    /** Whether the service routes by HTTP method + path (both REST variants). */
    boolean isRest() {
        return this == REST_JSON || this == REST_XML;
    }
}
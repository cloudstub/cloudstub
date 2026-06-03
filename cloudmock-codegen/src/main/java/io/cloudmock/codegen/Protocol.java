package io.cloudmock.codegen;

/**
 * The AWS request/response protocol used by a service, mapped to the corresponding
 * {@code StubRegistrar} method.
 */
enum Protocol {
    /** {@code @aws.protocols#awsJson1_0} or {@code #awsJson1_1} → {@code registerJsonTargetStub}. */
    JSON_TARGET,
    /** {@code @aws.protocols#query} or {@code #restXml} → {@code registerXmlFormStub}. */
    FORM_URL,
    /** {@code @aws.protocols#restJson1} → {@code registerRestStub}. */
    REST
}

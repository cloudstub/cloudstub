package io.cloudmock.codegen;

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Detects the AWS wire protocol for a service by inspecting its Smithy protocol traits.
 *
 * <p>Detection order mirrors CloudMock's routing table:
 * <ol>
 *   <li>JSON / X-Amz-Target — {@code @aws.protocols#awsJson1_0} or {@code #awsJson1_1}</li>
 *   <li>XML / Form URL — {@code @aws.protocols#query} or {@code #restXml}</li>
 *   <li>REST path — {@code @aws.protocols#restJson1}</li>
 * </ol>
 *
 * <p>If none of the above traits is present, the detector falls back to
 * {@link Protocol#JSON_TARGET} and prints a warning.
 */
class ProtocolDetector {

    private ProtocolDetector() {}

    static Protocol detect(ServiceShape service) {
        if (service.hasTrait(AwsJson1_0Trait.class) || service.hasTrait(AwsJson1_1Trait.class)) {
            return Protocol.JSON_TARGET;
        }
        if (service.hasTrait(AwsQueryTrait.class) || service.hasTrait(RestXmlTrait.class)) {
            return Protocol.FORM_URL;
        }
        if (service.hasTrait(RestJson1Trait.class)) {
            return Protocol.REST;
        }
        System.err.println("WARNING: no known protocol trait found on "
                + service.getId() + " — defaulting to JSON_TARGET. "
                + "Set the protocol explicitly if this is wrong.");
        return Protocol.JSON_TARGET;
    }
}

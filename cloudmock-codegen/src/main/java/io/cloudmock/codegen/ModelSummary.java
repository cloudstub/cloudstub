package io.cloudmock.codegen;

import java.util.List;

/**
 * The result of validating a Smithy model without generating module sources.
 *
 * @param serviceId the derived CloudMock service id (e.g. {@code "sqs"})
 * @param moduleName the derived module name (e.g. {@code "cloudmock-sqs"})
 * @param protocol the detected request/response protocol
 * @param operations the names of the service operations, sorted
 */
record ModelSummary(
        String serviceId, String moduleName, Protocol protocol, List<String> operations) {}

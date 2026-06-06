$version: "2"
namespace com.example

use aws.protocols#awsQuery
use aws.api#service

/// Fixture for FORM_URL (query) protocol builder tests.
@service(sdkId: "FormTest")
@awsQuery
service FormTestService {
    version: "2024-01-01"
    operations: [PublishMessage]
}

operation PublishMessage {
    input: PublishMessageInput
    output: PublishMessageOutput
}

@input
structure PublishMessageInput {
    message: String
}

@output
structure PublishMessageOutput {
    messageId: String
}

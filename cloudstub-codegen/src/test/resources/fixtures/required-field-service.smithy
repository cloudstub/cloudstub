$version: "2"
namespace com.example

use aws.protocols#awsJson1_1
use aws.api#service

/// Fixture to verify that @required output members become constructor parameters in the generated builder.
@service(sdkId: "RequiredField")
@awsJson1_1
service RequiredFieldService {
    version: "2024-01-01"
    operations: [CreateItem]
}

operation CreateItem {
    input: CreateItemInput
    output: CreateItemOutput
}

@input
structure CreateItemInput {
    name: String
}

@output
structure CreateItemOutput {
    @required
    itemId: String
    @required
    arn: String
    description: String
}

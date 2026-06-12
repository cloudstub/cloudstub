$version: "2"
namespace com.example

use aws.protocols#awsJson1_1

/// Minimal fixture for ModuleGenerator tests — JSON/X-Amz-Target protocol, two operations.
@awsJson1_1
service TestService {
    version: "2024-01-01"
    operations: [CreateFoo, DeleteFoo]
}

operation CreateFoo {
    input: CreateFooInput
    output: CreateFooOutput
}

@input
structure CreateFooInput {
    name: String
}

@output
structure CreateFooOutput {
    fooId: String
    name: String
}

operation DeleteFoo {
    input: DeleteFooInput
    output: DeleteFooOutput
}

@input
structure DeleteFooInput {
    fooId: String
}

@output
structure DeleteFooOutput {}

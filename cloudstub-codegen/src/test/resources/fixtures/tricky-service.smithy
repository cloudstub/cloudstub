$version: "2"
namespace com.example

use aws.protocols#awsJson1_1
use aws.api#service

/// Fixture exercising identifier sanitisation (#2), @jsonName wire overrides (#4),
/// and precise scalar type mapping (#5) in the generated response builder.
@service(sdkId: "Tricky")
@awsJson1_1
service TrickyService {
    version: "2024-01-01"
    operations: [Describe]
}

operation Describe {
    input: DescribeInput
    output: DescribeOutput
}

@input
structure DescribeInput {
    id: String
}

@output
structure DescribeOutput {
    /// Java reserved word as a required field — identifier must be sanitised, wire key kept verbatim.
    @required
    default: String

    /// Java reserved word + @jsonName — identifier sanitised to class_, wire key is "Class".
    @jsonName("Class")
    class: String

    createdAt: Timestamp
    amount: BigDecimal
    count: Long
    status: Status
}

enum Status {
    ACTIVE
    INACTIVE
}

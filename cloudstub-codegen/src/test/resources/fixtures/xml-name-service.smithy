$version: "2"
namespace com.example

use aws.protocols#restXml
use aws.api#service

/// Fixture verifying that @xmlName overrides the serialised element name for restXml builders (#4).
@service(sdkId: "XmlName")
@restXml
service XmlNameService {
    version: "2024-01-01"
    operations: [Fetch]
}

@http(method: "GET", uri: "/things/{id}")
operation Fetch {
    input: FetchInput
    output: FetchOutput
}

@input
structure FetchInput {
    @httpLabel
    @required
    id: String
}

@output
structure FetchOutput {
    @xmlName("Identifier")
    thingId: String
}

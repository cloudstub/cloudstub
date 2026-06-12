$version: "2"
namespace com.example

use aws.protocols#restXml
use aws.api#service

/// Fixture for the issue #0019 review (finding #3): real AWS models reference traits that are not
/// on the generator's classpath (e.g. smithy.rules#*, smithy.waiters#*). The generator must load
/// such models via ALLOW_UNKNOWN_TRAITS + disableValidation rather than aborting. This service
/// applies an undefined trait so the assembler would fail without that tolerance.
@service(sdkId: "Gadget")
@restXml
@com.undefined#madeUpTrait(foo: "bar")
service GadgetService {
    version: "2024-01-01"
    operations: [GetGadget]
}

@http(method: "GET", uri: "/gadgets/{id}")
@com.undefined#anotherUnknownTrait
operation GetGadget {
    input: GetGadgetInput
    output: GetGadgetOutput
}

@input
structure GetGadgetInput {
    @httpLabel
    @required
    id: String
}

@output
structure GetGadgetOutput {
    gadgetId: String
}

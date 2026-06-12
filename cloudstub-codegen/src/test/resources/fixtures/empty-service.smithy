$version: "2"
namespace com.example

use aws.api#service

/// Fixture for the issue #0019 review (finding #3): a service that resolves to zero operations.
/// The generator must fail loudly rather than emit a useless empty module, since validation is
/// disabled and this is the main safety net against the wrong/broken model being passed.
@service(sdkId: "Empty")
service EmptyService {
    version: "2024-01-01"
}

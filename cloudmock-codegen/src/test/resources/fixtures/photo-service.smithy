$version: "2"
namespace com.example

use aws.protocols#restXml
use aws.api#service

/// Minimal fixture for ModuleGenerator tests — restXml protocol (REST path routing, XML bodies).
@service(sdkId: "Photo")
@restXml
service PhotoService {
    version: "2024-01-01"
    operations: [GetPhoto, PutPhoto]
}

@http(method: "GET", uri: "/photos/{key+}")
operation GetPhoto {
    input: GetPhotoInput
    output: GetPhotoOutput
}

@input
structure GetPhotoInput {
    @httpLabel
    @required
    key: String
}

@output
structure GetPhotoOutput {
    photoId: String
    sizeBytes: Long
}

@http(method: "PUT", uri: "/photos/{key}")
operation PutPhoto {
    input: PutPhotoInput
    output: PutPhotoOutput
}

@input
structure PutPhotoInput {
    @httpLabel
    @required
    key: String
}

@output
structure PutPhotoOutput {}

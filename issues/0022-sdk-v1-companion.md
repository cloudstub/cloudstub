# cloudmock-sdk-v1 companion library

**Phase:** 3
**Type:** module

## Summary

AWS SDK v1 has no global endpoint override equivalent to the `aws.endpoint-url` system property used by SDK v2.
Redirecting SDK v1 clients to a CloudMock instance requires per-client configuration. Create a `cloudmock-sdk-v1`
companion library that provides a builder helper to redirect v1 clients to the CloudMock port. It should be a one-liner
in test setup — not zero-config like v2, but close enough that it does not slow adoption for teams still on SDK v1.

## Acceptance criteria

- [ ] A new `cloudmock-sdk-v1` subproject is added to `settings.gradle`
- [ ] The subproject declares `com.amazonaws:aws-java-sdk-core` as a `compileOnly` dependency — SDK v1 must not be forced onto consumers who do not need it
- [ ] A `CloudMockV1Endpoints` utility class provides a static helper that returns an `EndpointConfiguration` pointing at `http://localhost:<port>` with a dummy signing region (`us-east-1`)
- [ ] Usage requires exactly one extra line compared to a normal SDK v1 client setup — no XML, no properties file, no subclassing
- [ ] `cloudmock-sdk-v1` has no compile dependency on any other `cloudmock-*` module; the Gradle isolation check passes
- [ ] At least one integration test configures an SDK v1 client via the helper, sends a request to a running `CloudMock` instance, and asserts the response is served without a connection error
- [ ] The README "Limitations / out of scope" section is updated to reflect that SDK v1 is supported via the companion library

## Dependencies

- #0003 (core engine must be stable — the companion targets its port API)
- #0005 (at least one service module should be live so the integration test uses a real stub)

## Notes

- The `cloudmock-core` engine does not change. It has no knowledge of which SDK version the consumer is using.
- SDK v1 uses `com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration` to override the endpoint. The
  helper should accept either a `CloudMock` instance or a raw port integer — whichever is simpler to use from a test.
- A dummy signing region is required by SDK v1's endpoint configuration but has no effect on stub matching. Document
  this clearly so users are not confused by the hardcoded value.
- `cloudmock-sdk-v1` is intentionally thin. It must not reimplement any stub logic — all response behaviour is owned
  by the installed service modules.

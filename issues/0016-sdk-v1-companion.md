# cloudmock-sdk-v1 companion library

**Phase:** 2
**Type:** module

## Summary

Deliver a small companion library (`cloudmock-sdk-v1`) that enables AWS SDK v1 users to redirect their clients to a
running CloudMock instance. The core engine is unchanged — it boots WireMock and serves stubs exactly as it does for
SDK v2. The companion library is a separate, opt-in concern: it provides a builder helper or client factory that points
an existing SDK v1 client builder at the CloudMock port. SDK v2 users are unaffected and continue to get zero-config
endpoint redirection via `aws.endpoint-url`.

## Acceptance criteria

- [ ] A new `cloudmock-sdk-v1` subproject is added to `settings.gradle`
- [ ] The subproject declares the AWS SDK v1 (`com.amazonaws:aws-java-sdk-core`) as a `compileOnly` dependency — it must not force SDK v1 onto consumers who do not need it
- [ ] A `CloudMockV1Endpoints` utility class provides a static helper that returns an `EndpointConfiguration` (or equivalent) pointing at `http://localhost:<port>` with a dummy signing region
- [ ] Usage requires exactly one extra line compared to a normal SDK v1 client setup — no XML, no properties file, no subclassing
- [ ] `cloudmock-sdk-v1` has no compile dependency on any other `cloudmock-*` module; the isolation constraint from #0001 continues to pass
- [ ] At least one integration test configures an SDK v1 client via the helper, sends a request to a running `CloudMock` instance, and asserts it reaches WireMock without a connection error
- [ ] The README "Scope and limitations" note is updated to reflect that SDK v1 is supported via the companion library

## Dependencies

- #0003 (core engine must be stable before the companion can target its port API)

## Notes

- The `cloudmock-core` engine does not change. It does not know which SDK version the consumer is using.
- SDK v1 uses `com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration` to override the endpoint. The
  helper should wrap this so callers pass only the `CloudMock` instance (or just the port integer).
- A dummy signing region (e.g. `"us-east-1"`) is required by SDK v1's endpoint configuration but has no effect on stub
  matching — document this clearly so users are not confused.
- `cloudmock-sdk-v1` is intentionally thin. It must not reimplement any stub logic — all response behaviour is owned by
  the installed service modules.
- Ship after the Phase 2 reference modules (`cloudmock-sqs`, `cloudmock-secretsmanager`) are proven, so the integration
  test can use a real module rather than a raw WireMock stub.

# S3 integration test in the example application

**Phase:** 3
**Type:** testing
**Parent:** #0019 (sub-issue)

## Summary

`cloudmock-s3` ships with its own module-level integration tests (#0019), but the `cloudmock-example`
Spring Boot application — the project's end-to-end proof from a real consumer's perspective — only
exercises SQS and Secrets Manager. Add S3 coverage to the example app: a small `@Service` that wraps
`S3Client`, an `S3Client` bean in `AwsConfig`, and a `@SpringBootTest` integration test that drives the
service through CloudMock via the JUnit 6 extension. This validates that S3 works through the same
Spring-wired, system-property-redirected path that SQS and Secrets Manager already prove, and serves as
living documentation for S3 users.

**The point of this test is that the application is written for real AWS S3 and mocked transparently.**
The `S3Client` bean must therefore be configured exactly as a production app would configure it — a
vanilla, virtual-hosted-style client whose only CloudMock touch-point is the `aws.endpoint-url` redirect
that SQS and Secrets Manager already rely on. No `pathStyleAccessEnabled`, no `checksumValidationEnabled`,
no other mock-specific settings may leak into application code; transparent virtual-hosted-style handling
is delivered by the interceptor in #0026. If the app needs S3-specific test config to pass, this issue is
not yet done — that's the signal #0026 is incomplete.

## Acceptance criteria

- [ ] `cloudmock-example/build.gradle` adds `libs.aws.s3` to `implementation` and `project(':cloudmock-s3')`
      to `testImplementation`
- [ ] `AwsConfig` provides an `S3Client` bean following **exactly** the existing
      `@Value("${aws.endpoint-url:}")` redirect pattern used for SQS/Secrets Manager — a vanilla
      virtual-hosted-style client with **no** `pathStyleAccessEnabled` and **no** `checksumValidationEnabled`
      override (production-faithful; transparency comes from #0026, not from the app)
- [ ] A new `@Service` (e.g. `ObjectStore`) wraps `S3Client` with a small realistic API
      (e.g. `createBucket`, `put(key, body)`, `get(key)`, `list()`)
- [ ] A `@SpringBootTest` integration test using `@RegisterExtension CloudMockExtension` exercises a
      create-bucket → put → get → list flow and asserts plausible non-null results
- [ ] The integration test contains no S3-specific client configuration or workarounds — it only
      autowires the service and asserts (same shape as `EventPublisherIntegrationTest`)
- [ ] All tests pass with `./gradlew :cloudmock-example:test`
- [ ] The module isolation constraint still holds — `cloudmock-example` does not bypass it

## Dependencies

- #0026 (transparent virtual-hosted-style S3 support — without it a vanilla app client misroutes)
- #0019 (cloudmock-s3 must be implemented)
- #0015 (example project scaffolding)

## Notes

- Mirror the existing `EventPublisher` / `SecretLoader` + `*IntegrationTest` structure; do not add
  business logic beyond what's needed to exercise the public API.
- The `S3Client` bean is deliberately vanilla. The `cloudmock-s3` **module** tests use
  `pathStyleAccessEnabled(true)` because the test owns its client; the **example app** must not, because
  it represents production code. Transparent virtual-hosted-style handling is #0026's job.
- Keep assertions focused: verify the SDK parses responses without throwing and that key fields
  (e.g. the object body is non-null/parseable, `listObjectsV2` returns a non-null result) are plausible.
  Object state is not simulated (Stage 1 contract mocking), so `get` returns CloudMock's synthetic body,
  not whatever was `put` — assert on non-null/parseable, not on exact echo.

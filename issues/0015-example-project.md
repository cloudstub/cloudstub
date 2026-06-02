# Example project for end-to-end validation

**Phase:** 2
**Type:** testing

## Summary

Add a self-contained example project (Gradle subproject `cloudmock-example`) that acts as a realistic consumer of the library. It depends on `cloudmock-core` and the Phase 2 modules (`cloudmock-sqs`, `cloudmock-secretsmanager`) as published JARs, and runs integration tests that exercise the full stack — start CloudMock, perform real AWS SDK v2 calls, assert correct responses. This project is the primary proof that the library works from a user's perspective.

## Acceptance criteria

- [ ] A `cloudmock-example` subproject is added to `settings.gradle`
- [ ] The subproject depends on `cloudmock-core`, `cloudmock-sqs`, and `cloudmock-secretsmanager` via `testImplementation`
- [ ] At least one integration test per Phase 2 module is included:
  - SQS: `CreateQueue`, `SendMessage`, `ReceiveMessage` round-trip
  - Secrets Manager: `CreateSecret`, `GetSecretValue` round-trip
- [ ] Tests use the JUnit 5 extension (`@ExtendWith(CloudMockExtension.class)`) if available, or start/stop CloudMock manually otherwise
- [ ] All tests pass with `./gradlew :cloudmock-example:test`
- [ ] The module isolation constraint still holds — `cloudmock-example` does not bypass it
- [ ] A short README section (or inline test Javadoc) shows the minimal setup needed to use CloudMock in a user project

## Dependencies

- #0005 (cloudmock-sqs)
- #0007 (cloudmock-secretsmanager)

## Notes

- This subproject intentionally mimics what an external consumer would do. Avoid importing internal CloudMock types beyond the public API (`CloudMock`, `CloudMockService`, `StubRegistrar`).
- Do not add business logic — the sole purpose is to validate the library's public surface and serve as living documentation.
- Keep test assertions focused: verify that the AWS SDK v2 client parses the response without throwing, and that key fields (e.g. `QueueUrl`, `MessageId`, `SecretString`) have plausible non-null values.
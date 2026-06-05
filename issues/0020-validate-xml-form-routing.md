# Validate XML/Form routing end-to-end

**Phase:** 3
**Type:** testing

## Summary

SQS turned out to use JSON/X-Amz-Target in AWS SDK v2, not XML/Form as originally assumed. As a result,
`registerXmlFormStub` has never been exercised by a real module. Either build a lightweight SNS module or write a
dedicated integration test that proves XML/Form routing works end-to-end through the core engine. Until this is done,
a bug in that code path could sit undetected indefinitely.

## Acceptance criteria

- [ ] At least one of the following is delivered:
  - A `cloudmock-sns` module (thin — covers `Publish`, `Subscribe`, `CreateTopic` at minimum) that uses `registerXmlFormStub` exclusively, with integration tests driven by the AWS SDK v2 SNS client; **or**
  - A standalone integration test in `cloudmock-core` or a new `cloudmock-test-xml-form` subproject that sends a raw HTTP request with an `Action` form body parameter and asserts it is matched and served by a stub registered via `registerXmlFormStub`
- [ ] The test or module is included in `./gradlew build` and passes in CI
- [ ] If a `cloudmock-sns` module is chosen, it follows the same isolation rules as all other service modules
- [ ] CLAUDE.md is updated to reflect whether `registerXmlFormStub` is now exercised by a real module

## Dependencies

- #0003 (core engine — `registerXmlFormStub` must be stable before exercising it)

## Notes

- The XML/Form protocol is used by SNS (legacy) and SQS v1 (not targeted). SNS is the most practical vehicle for
  validating this path because it is still used in production workloads.
- If a standalone integration test is chosen instead of a module, it must use the same `StubRegistrar` interface that
  module authors use — not a WireMock type directly — so the test validates the public contract, not the internals.
- The decision between a thin SNS module and a bare integration test should be driven by team capacity. The integration
  test is a faster path; the SNS module has more long-term value.

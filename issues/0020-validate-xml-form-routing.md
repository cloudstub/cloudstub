# Validate XML/Form routing end-to-end

**Phase:** 3
**Type:** testing

## Summary

SQS turned out to use JSON/X-Amz-Target in AWS SDK v2, not XML/Form as originally assumed. As a result,
`registerXmlFormStub` has never been exercised by a real module. Either build a lightweight SNS module or write a
dedicated integration test that proves XML/Form routing works end-to-end through the core engine. Until this is done,
a bug in that code path could sit undetected indefinitely.

## Acceptance criteria

- [x] At least one of the following is delivered:
  - A `cloudmock-sns` module (thin — covers `Publish`, `Subscribe`, `CreateTopic` at minimum) that uses `registerXmlFormStub` exclusively, with integration tests driven by the AWS SDK v2 SNS client; **or**
  - A standalone integration test in `cloudmock-core` or a new `cloudmock-test-xml-form` subproject that sends a raw HTTP request with an `Action` form body parameter and asserts it is matched and served by a stub registered via `registerXmlFormStub`
- [x] The test or module is included in `./gradlew build` and passes in CI
- [x] If a `cloudmock-sns` module is chosen, it follows the same isolation rules as all other service modules
- [x] CLAUDE.md is updated to reflect whether `registerXmlFormStub` is now exercised by a real module

## Dependencies

- #0003 (core engine — `registerXmlFormStub` must be stable before exercising it)

## Notes

- The XML/Form protocol is used by SNS (legacy) and SQS v1 (not targeted). SNS is the most practical vehicle for
  validating this path because it is still used in production workloads.
- If a standalone integration test is chosen instead of a module, it must use the same `StubRegistrar` interface that
  module authors use — not a WireMock type directly — so the test validates the public contract, not the internals.
- The decision between a thin SNS module and a bare integration test should be driven by team capacity. The integration
  test is a faster path; the SNS module has more long-term value.

## Implementation & code review fixes

Delivered the `cloudmock-sns` module (chosen for long-term value), generated from the real AWS SNS Smithy model via
`cloudmock-codegen`, with the 6 core operations (`CreateTopic`, `Publish`, `Subscribe`, `ListTopics`, `DeleteTopic`,
`Unsubscribe`) given reviewed XML responses and integration tests driven by the AWS SDK v2 `SnsClient`, plus a raw-HTTP
test that asserts `Action=…` form routing through `registerXmlFormStub`. The remaining ~40 operations are unreviewed
codegen placeholders (same approach as `cloudmock-s3`).

A code review surfaced one fragility, now fixed:

- **`registerXmlFormStub` substring matching was collision-safe only by accident of ordering.** The matcher used
  `containing("Action=" + actionName)`, so a request for a longer action (`Action=PublishBatch`) also matched a
  shorter prefix stub (`Action=Publish`); correctness relied on alphabetical registration order plus WireMock's
  last-registered-wins tie-break. Hardened to a boundary-anchored full-match regex
  (`(?s)(.*&)?Action=<quoted-name>(&.*)?`) in `cloudmock-core`'s `WireMockStubRegistrar`, so an action only matches a
  complete `Action=<name>` form parameter regardless of registration order. `Pattern.quote` guards action names
  containing regex metacharacters.
- **Regression test added** in `cloudmock-core` (`XmlFormRoutingTest`, 3 tests): a `Publish`-only service must return
  404 for an `Action=PublishBatch` request (the pin — returned 200 under the old matcher), plus exact-match and
  not-first-parameter cases. Placed in core, not the SNS module, because with the full SNS service registered both the
  old and new matcher pass (LIFO masked the bug) — the partial-registration scenario is the only place the fix is
  observable.

`./gradlew build` passes. SNS suite: 7 tests; new core routing suite: 3 tests.

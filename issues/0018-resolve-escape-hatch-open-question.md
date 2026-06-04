# Resolve open question: StubRegistrar escape hatch

**Phase:** 3
**Type:** housekeeping

## Summary

Open question #1 in CLAUDE.md asks whether `StubRegistrar` should expose a raw WireMock `MappingBuilder` for advanced
module authors. The decision is no. The three routing methods — `registerJsonTargetStub`, `registerXmlFormStub`, and
`registerRestStub` — cover all planned AWS service protocols. Exposing a `MappingBuilder` escape hatch would leak
WireMock types into the public SPI, making it impossible to swap the underlying HTTP engine without a breaking change.
Remove the open question from CLAUDE.md and record the rationale so the question is not reopened without new evidence.

## Acceptance criteria

- [x] The open question is removed from the "Open questions" section of CLAUDE.md
- [x] A brief rationale is added to the SPI contract section of CLAUDE.md explaining why the escape hatch was explicitly rejected
- [x] No change is made to the `StubRegistrar` interface or any implementing class
- [x] All existing SPI and module tests continue to pass after the documentation change

## Dependencies

- #0002 (SPI contract is the subject of this decision)

## Notes

- The core principle is that WireMock is an implementation detail of `cloudmock-core`. No WireMock type should appear
  in any public CloudMock API, including `StubRegistrar`.
- If a future service requires routing logic that cannot be expressed through the three existing methods, the correct
  path is to add a new method to `StubRegistrar` — not to expose a `MappingBuilder`. That would be a minor SPI
  revision processed through the normal issue flow.
- This issue is documentation-only. No code changes are required.

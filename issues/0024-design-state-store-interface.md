# Design the state store interface

**Phase:** 4
**Type:** design

## Summary

When stateful simulation begins (Stage 2 in the project vision), modules will need an injectable state backend: a place
to store message queues, object contents, item counts, and similar mutable state so that one call can affect the
response to a later call. Review the current SPI to verify that a state store can be added without breaking changes,
and sketch the interface that modules and external consumers (e.g. a future management console) would use. No
implementation is required — the goal is to confirm the door is open and identify any SPI adjustments needed before
the contract is too widely adopted to change.

## Acceptance criteria

- [ ] The current `CloudMockService` and `StubRegistrar` SPI is reviewed and documented for compatibility with a future state store injection point
- [ ] A proposed `StateStore` interface sketch is added to this issue (or a linked design note) covering at minimum: `put(String serviceId, String key, Object value)`, `get(String serviceId, String key)`, `list(String serviceId)`, and `clear(String serviceId)`
- [ ] The review identifies whether `StateStore` can be passed to `register(StubRegistrar registrar)` without a breaking change, or whether a second `register` overload or a context object is needed
- [ ] The review confirms that read-only queries from an external consumer (e.g. a management console HTTP endpoint) can be served from the same `StateStore` interface without exposing internal WireMock types
- [ ] Any SPI adjustments that would be easier to make now than later are flagged with a recommended action
- [ ] No implementation code is merged as part of this issue

## Dependencies

- #0002 (SPI contract — any adjustments must be evaluated against the frozen interface)
- #0003 (core engine — state store lifecycle would be managed here)

## Notes

- This is a design review, not an implementation. The deliverable is a written analysis and interface sketch, not
  merged code.
- The management console (see project memory) requires a read-only query interface over mock state. The `StateStore`
  must not require a WireMock type on either side of its API — it must be usable by both module code and an HTTP
  handler that has no knowledge of WireMock internals.
- Consider whether the state store should be scoped per service (`sqs`, `s3`) or global with a service-id key prefix.
  Both approaches are valid; the choice affects query patterns from external consumers.
- If the SPI review reveals that adding a `StateStore` injection point would require a breaking change, that is the
  primary output of this issue — surface it early so it can be scheduled before the interface is widely adopted.

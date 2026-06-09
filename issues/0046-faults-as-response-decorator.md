# Model fault injection as a response decorator, not a parallel stub

**Type:** core

## Summary

`FaultEngine` injects faults by registering **separate, higher-priority WireMock stubs** that
re-declare the response definition for each affected operation (`throttleMapping`, `timeoutMapping`,
`brownoutProbabilisticMapping`, `brownoutAlwaysMapping` in
`cloudmock-core/.../internal/FaultEngine.java`). This is a second, parallel response-production path
that mirrors the normal stub's response. Every time the normal response model grows, the fault path
has to mirror the growth:

- Stateful handlers ([0044](0044-stateful-stub-handlers.md)) forced `FaultEngine` to learn about
  `handlerKey` and re-attach the `StatefulResponseTransformer` to timeout/brownout mappings, with a
  deliberate ordering ("stateful first, brownout second") expressed as a comment rather than enforced.
- Per-operation response headers (added to `StubResponse` in 0044) would need the same mirroring if a
  fault ever has to preserve them.

The duplication is a recurring maintenance tax: the fault path and the normal path drift unless every
response feature is implemented twice. The deeper model is **fault as a decorator over the normal
response** — the real stub still produces the response (including running its handler exactly once),
and the fault wraps it (delay it, reset the connection, or replace it with a throttling error) — so
new response features need no `FaultEngine` change at all.

## Acceptance criteria

- [ ] Faults are applied as a decoration/transformation over the existing matched stub's response,
  rather than a re-declared parallel stub that duplicates the response definition
- [ ] Adding a new response capability (headers, statefulness, etc.) requires no change to the fault
  layer — the fault composes over whatever the normal path produced
- [ ] `FaultEngine` no longer special-cases `handlerKey`; the stateful handler runs through the
  normal response path, and the fault layer is unaware of it
- [ ] A faulted stateful request runs its handler **at most once** (no double-execution from the
  fault path re-invoking the handler), and a hard fault (throttle, connection reset) does not run the
  handler at all where the body is discarded
- [ ] Public fault API is unchanged: `CloudMock.simulateThrottle/simulateTimeout/simulateNetworkBrownout`,
  `clearFaults`, `clearAllFaults`, and the `@Simulate*` annotations behave as before
- [ ] Existing fault-injection tests (`cloudmock-junit6` `FaultInjectionTest`, `StatefulStubTest`)
  pass unchanged; add coverage for timeout/brownout over a stateful stub

## Dependencies

- [0044](0044-stateful-stub-handlers.md) (stateful handlers — the special-casing in `FaultEngine`
  that this refactor removes)

## Notes

- Today's mechanism is WireMock priority stubs (`atPriority(1)`) that override the default-priority
  service stubs. A decorator model likely means a single fault-aware `ResponseTransformerV2` (or
  per-service state) applied to the real stub, rather than shadow stubs — but the implementation is
  open as long as the acceptance criteria hold.
- The "0044 timeout fix" already encodes the desired semantics for one case (timeout must NOT run the
  handler; probabilistic brownout must). A decorator model should make that distinction a property of
  the fault type, expressed once, not duplicated per fault-builder method.
- Keep the behaviour that a single-service reset/clear restores normal responses without touching the
  underlying stubs.

# Stateful stub handlers — wire the state store into the request path

**Type:** core

## Summary

Issue [0035](0035-implement-state-store.md) delivered the state store: it is instantiated, lifecycle-managed,
persistent, thread-safe, injected into every module via `CloudMockContext.stateStore()`, and exposed to the admin
REST API. But **nothing on the request path reads or writes it.** The only way a module produces a response is
through `StubRegistrar`'s three methods, all of which register a static Handlebars template. WireMock evaluates
those templates at request time with access only to the request and the stateless helpers (`md5`, `randomValue`,
`jsonPath`) — none of which can reach the `StateStore`. No service module references `context.stateStore()` today
(only the admin `ApiServer` does, and only for reads).

The result: CloudMock still serves stateless responses in both embedded and standalone mode. What a user sends in
one call does not come back in the next. The store sits empty.

This issue adds the missing **request-time bridge**: a way for a module to run its own Java code on a matching
request, read and write the shared `StateStore`, and return the response. This is the mechanism [0024](0024-design-state-store-interface.md)
described — "the module is the bridge between the AWS SDK protocol and the store" — and the consumer of the typed
response builders from [0036](0036-codegen-stateful-response-builders.md).

## Approach (Option A — handler-based stubs)

Two alternatives were considered:

- **A. Handler-based stubs** — add handler overloads to `StubRegistrar` that register a Java function
  `(StubRequest, StateStore) -> StubResponse`, backed internally by a WireMock `ResponseTransformerV2`. The store
  stays a dumb key-value backend; all AWS-to-store translation lives in module Java code.
- **B. State-backed Handlebars helpers** — add `{{statePut}}` / `{{stateGet}}` / `{{stateList}}` helpers, keeping
  the template methods. Rejected: it smears the translation logic across template strings and core helpers, and
  stateful operations (pop-on-receive, delete, enumerate) are painful-to-impossible to express in Handlebars.

Option A is chosen: it keeps the store ignorant of modules, keeps modules independent of each other, hides WireMock
behind small SPI value types, and puts each module's translation logic in plain Java — matching the loose
architecture and 0024's intent. Extending `StubRegistrar` is the sanctioned escape path documented in `CLAUDE.md`
("add a new method to `StubRegistrar` via the normal issue flow"); the additions are overloads, so existing
template stubs are untouched.

## Acceptance criteria

- [x] New public SPI types in `cloudmock-core`'s `spi` package, none exposing a WireMock type:
  - [x] `StubRequest` — read-only view of the incoming request (`method`, `path`, `body`, `header(name)`,
    `queryParam(name)`)
  - [x] `StubResponse` — value object (status, content-type, body) with `json(...)` / `xml(...)` / `of(...)` factories
  - [x] `StubHandler` — functional interface `(StubRequest, StateStore) -> StubResponse`
- [x] Handler overloads on `StubRegistrar` for all three protocols, alongside the existing template methods:
  - [x] `registerJsonTargetStub(String target, StubHandler handler)`
  - [x] `registerXmlFormStub(String actionName, StubHandler handler)`
  - [x] `registerRestStub(HttpMethod method, String pathPattern, StubHandler handler)`
- [x] Internal `ResponseTransformerV2` (modeled on `BrownoutTransformer`) invokes the handler with the shared
  `StateStore` and converts `StubResponse` back to a WireMock response; WireMock stays hidden
- [x] The `StateStore` is threaded to the transformer; handler stubs coexist with the existing fault-injection
  stubs (throttle/timeout/brownout still apply to a service whose stubs are handler-based)
- [x] `cloudmock-sqs` rewritten as the reference stateful module: `CreateQueue`, `SendMessage`, `ReceiveMessage`,
  `DeleteMessage`, `DeleteQueue`, `ListQueues` backed by the store, so a `SendMessage` then `ReceiveMessage`
  returns the message that was sent
- [x] A full `POST /api/reset` clears the store (`StateStore.clearAll()`); a single-service reset clears only that
  service's prefix (`StateStore.clear(serviceId + "/")`)
- [x] Tests: SQS send→receive round-trip through the AWS SDK, delete removes the message, and state survives a
  restart when a store directory is configured
- [x] Existing template-based modules (`cloudmock-sns`, `cloudmock-s3`, `cloudmock-secretsmanager`) continue to
  pass unchanged
- [x] `CLAUDE.md` updated: statelessness caveat replaced with the stateful-handler description; `StubRegistrar`
  contract section extended

## Dependencies

- [0024](0024-design-state-store-interface.md) (state store design — the injection point and the "module is the bridge" model)
- [0035](0035-implement-state-store.md) (state store implementation — the store this issue finally connects to the request path)
- [0036](0036-codegen-stateful-response-builders.md) (typed response builders — the artefact a stateful handler returns)

## Notes

- The handler is module-local code; it never references another module, and the store gains no AWS knowledge —
  the loose-architecture invariants are preserved.
- Handlers receive parsed request data and the store only. They must not import WireMock, the AWS SDK, jackson, or
  picocli — mirroring the `CloudMockApiService` handler constraint.
- Stateful behaviour must be identical in embedded and standalone mode, since both run the same core engine.
- Out of scope (unchanged from `CLAUDE.md`): SQS FIFO dedup/ordering, S3 multipart lifecycle, DynamoDB conditional
  expressions/transactions, IAM evaluation.

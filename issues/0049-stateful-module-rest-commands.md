# Make the module REST/API commands stateful (complete #0035's read-query exposure)

**Type:** core

## Summary

CloudMock has two front doors and only one is stateful. The **AWS-SDK path** (mock port, `X-Amz-Target`)
reads and writes the state store via the `StubHandler` mechanism ([0044](0044-stateful-stub-handlers.md)).
The **REST/CLI/console path** (API port, `/api/<service>/…`) is still stateless: `CloudMockApiService`
handlers receive only `ApiRequest → ApiResponse` with **no `StateStore`**, so e.g.
`CloudMockSqsApiService.receiveMessage` returns a hardcoded `cloudmock-synthetic-message` and never
sees what was sent.

The CloudMock Console is a thin client over the API port; its service browser calls these module
commands, so it shows synthetic data — a message sent through the AWS SDK does not appear in the
console. This is the unmet half of [0035](0035-implement-state-store.md): its acceptance criterion
"Core exposes the store to the admin REST API for read queries" was checked, but the only place the
admin API touches the store is `reset` (`clear`/`clearAll`) — there is no read path. It is exactly the
"infrastructure marked done without an end-to-end consumer" failure that [0048](0048-spi-evolution-policy-and-infra-done.md)
calls out; the console is the consumer that surfaced it.

## The fix

Make the module REST commands stateful by giving `CloudMockApiService` handlers access to the same
`StateStore`, then rewrite the SQS API handlers to read/write the same `sqs/queues/…` keys the AWS
handler uses. The console needs **no change** — it already calls these endpoints; they just start
returning real data. A message sent via the AWS SDK then shows up in the console, and vice versa
(exactly the [0024](0024-design-state-store-interface.md) vision: "immediately visible in the console").

Concretely:

1. **SPI** — thread the store into API registration, mirroring `CloudMockContext` (which already gives
   `CloudMockService` both `registrar()` and `stateStore()`). The `ApiServer` already holds
   `cloudMock.stateStore()`; pass it through to the module's route handlers.
2. **SQS API service** — `list-queues`, `receive-message`, `send-message`, `purge-queue` read/write
   the store, sharing the key helpers with `CloudMockSqsService` so the two surfaces can't drift.
3. **Docs** — update the now-wrong "stateless responses" notes in `console.md` / `cli.md` / `CLAUDE.md`.

## Acceptance criteria

- [ ] `CloudMockApiService` handlers can read and write the shared `StateStore`, threaded through API
  registration in the same shape as `CloudMockContext` (registrar + state store), with no WireMock or
  AWS-SDK type exposed
- [ ] The `StateStore` reaching the API handlers is the **same instance** the AWS-protocol handlers use
  (one store per running CloudMock), so the two surfaces see each other's data
- [ ] SQS API commands are state-backed against the `sqs/queues/…` keys: `send-message` writes a
  message, `receive-message` returns previously sent messages, `list-queues` lists created queues,
  `purge-queue` clears a queue's messages
- [ ] The SQS key scheme is defined once and shared between `CloudMockSqsService` and
  `CloudMockSqsApiService` (extract package-private key helpers) so the AWS path and the REST path
  cannot diverge
- [ ] End-to-end: a message sent via the AWS SDK (mock port) is returned by `GET /api/sqs/receive-message`
  (API port), and a message sent via `POST /api/sqs/send-message` is returned by the AWS SDK
- [ ] The console shows real data with **no console-repo change** (it already calls these routes)
- [ ] `POST /api/reset` (all) and `?service=sqs` continue to clear the store as today
- [ ] Docs corrected: the "synthetic and stateless" notes in `docs/console.md`, `docs/cli.md`, and the
  API-service section of `CLAUDE.md` no longer claim the REST surface is stateless

## Dependencies

- [0035](0035-implement-state-store.md) (state store — this completes its unmet "expose to the admin
  REST API for read queries" criterion)
- [0024](0024-design-state-store-interface.md) (design — "the admin REST API, CLI, and console can all
  read from the store directly")
- [0044](0044-stateful-stub-handlers.md) (the AWS-path stateful handlers whose store/keys this reuses)
- [0034](0034-management-console-ui.md) (the console that consumes these routes)

## Notes

- Scope this pass to **SQS** (the reference stateful module). `cloudmock-s3` and `cloudmock-secretsmanager`
  API services can be made stateful in follow-ups; their handlers may keep the new store-aware signature
  without using it yet.
- `receive-message` over the REST surface stays **non-destructive** (it does not delete on read), matching
  the AWS-path handler and suiting a console "browse what's stored" view.
- Consistent with the project principle that CloudMock is a mock, not an AWS replica: the goal is that
  data put in comes back out across both surfaces, not full AWS API/visibility-timeout fidelity.
- Reads go through module code (as 0024 requires) — the store gains no AWS knowledge.

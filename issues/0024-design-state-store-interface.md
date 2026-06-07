# Design the state store interface

**Type:** design

## Summary

CloudMock is currently stateless — every response is a template. A module receives an AWS SDK call and returns a
well-formed response, but nothing is remembered between calls. The state store is what changes that. It is a shared,
core-managed key-value backend that modules read from and write to so that one call can affect the response to a
later call.

When a user sends a message to SQS, the module writes it to the store. When the user receives a message, the module
reads from the store and returns what was actually sent. When the user deletes a message, the module removes it from
the store. The module is the bridge between the AWS SDK protocol and the store — it knows how to translate AWS API
calls into store reads and writes. The store has no AWS knowledge at all.

Because the store is shared and owned by the core, the admin REST API, the CLI, and the management console can all
read from it directly. Whatever a user's application just sent to SQS is immediately visible in the console without
any extra wiring. A single `reset()` clears everything across all services.

This issue is a design review only. The goal is to define the store interface and confirm it can be injected into
the existing SPI without breaking changes. No implementation is required.

## Data model

The store is a key-value backend scoped by service ID. Each module writes its data under its own service prefix:

- `sqs/queues/my-queue/messages/{id}`
- `s3/buckets/my-bucket/objects/{key}`
- `secrets/my-secret`

If a module is not loaded, there are simply no entries under its service prefix. The store has no concept of which
services exist — data appears when a module writes it and disappears when reset is called.

## Acceptance criteria

- [x] The store's role as the live data backend for modules is clearly documented
- [x] The `StateStore` interface is sketched covering at minimum: `put`, `get`, `list`, and `clear` scoped by
  service ID
- [x] The store injection point is designed into the SPI in whatever way is cleanest — breaking changes are acceptable since CloudMock is not yet published
- [x] The review confirms the store interface has no WireMock types on either side — it must be usable by module
  code, the admin REST API, and the console equally
- [x] The key naming convention is proposed and documented
- [x] Any SPI adjustments needed before the interface is too widely adopted to change are flagged with a
  recommended action
- [x] No implementation code is merged as part of this issue

## Dependencies

- 0002 (SPI contract — any adjustments must be evaluated against the frozen interface)
- 0003 (core engine — state store lifecycle would be managed here)

## Design analysis

### SPI injection point

Since breaking changes are acceptable, the cleanest approach is to replace the current `register(StubRegistrar)`
signature with a context object that carries both the registrar and the store:

```java
public interface CloudMockService {
    String serviceId();
    void register(CloudMockContext context);
}
```

```java
public interface CloudMockContext {
    StubRegistrar registrar();
    StateStore stateStore();
}
```

This is strictly better than `register(StubRegistrar, StateStore)` because future additions (e.g. a metrics sink,
a config object) extend `CloudMockContext` without ever touching the `CloudMockService` signature again.

`CloudMockContext` lives in `cloudmock-core`'s public SPI package alongside `CloudMockService` and `StubRegistrar`.
The implementation class stays in `cloudmock-core`'s internal package, as `WireMockStubRegistrar` does today.

### StateStore interface

```java
public interface StateStore {

    /** Store a value. Overwrites any existing entry at the same key. */
    void put(String key, Object value);

    /** Retrieve a value, or {@code null} if the key does not exist. */
    Object get(String key);

    /**
     * List all keys whose path begins with {@code prefix}.
     * Passing {@code "sqs/"} lists every key the SQS module has written.
     * Returns an empty list if no keys match.
     */
    List<String> list(String prefix);

    /** Delete a single entry. No-op if the key does not exist. */
    void delete(String key);

    /** Delete all entries whose path begins with {@code prefix}. */
    void clear(String prefix);

    /** Delete all entries in the store. Called by the global reset endpoint. */
    void clearAll();
}
```

No WireMock types appear on either side. The admin REST API, CLI, and console all hold a reference to `StateStore`
and call `list` and `get` directly with no knowledge of WireMock internals.

### Key naming convention

Keys follow a path-like hierarchy: `{serviceId}/{resource-type}/{name}[/{subresource}/{id}]`

| Module | Key examples |
|---|---|
| `cloudmock-sqs` | `sqs/queues/my-queue`, `sqs/queues/my-queue/messages/abc-123` |
| `cloudmock-s3` | `s3/buckets/my-bucket`, `s3/buckets/my-bucket/objects/my-key` |
| `cloudmock-secretsmanager` | `secrets/my-secret` |

The store does not enforce prefixing — it is a module-authoring convention. The module authoring guide should
document the convention and include the `serviceId()` return value as the mandatory first segment.

### Lifecycle

`CloudMock.start()` creates the `StateStore` (initially empty) and the `CloudMockContext` that wraps it alongside
the `WireMockStubRegistrar`. Each module receives the same context instance. `CloudMock.stop()` discards it.
`CloudMock` should also expose `stateStore()` as a public method so tests and the admin layer can reach the store
after startup.

### Persistence

The store must be persistent — state must survive a CloudMock restart. Making `StateStore` an interface from the
start (which this design does) allows the implementation to be swapped without touching the SPI. The choice of
persistence backend (embedded file store, SQLite, etc.) is an implementation detail left to issue
[0035](0035-implement-state-store.md).

### Recommended actions

1. **Rename `register(StubRegistrar)` → `register(CloudMockContext)`** before any more modules are written — the
   change touches every module's `register` method signature and is easier to make now than after ten modules exist.
2. **Expose `cloudMock.stateStore()`** on the public `CloudMock` API so tests can inspect state directly without
   going through the AWS SDK.
3. **Document the key convention** in the module authoring guide as a required naming rule, not a suggestion.

Implementation tracked in [0035](0035-implement-state-store.md).

## Notes

- The store is owned and lifecycle-managed by the core. Modules do not create or destroy it.
- The store is injected into each module at registration time alongside the StubRegistrar.
- Modules scope all their data by service ID. The store does not enforce this — it is a convention that
  module authors follow.
- The admin REST API, CLI, and console are all read consumers of the same store. They never write directly —
  all writes go through module code triggered by AWS SDK calls.
- Consider whether the store should be an in-memory map (fast, simple, lost on restart) or pluggable with a
  persistence backend for longer-running local development sessions.

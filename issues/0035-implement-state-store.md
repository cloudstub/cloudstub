# Implement the state store

**Type:** core

## Summary

Implement the state store designed in 0024. The store is a shared, core-managed key-value backend injected into
every module at registration time. It is the mechanism that turns CloudMock from a stateless stub server into a
service that returns live data — what a user sends in one call comes back in the next.

## Acceptance criteria

- [ ] `StateStore` interface implemented as designed in [0024](0024-design-state-store-interface.md)
- [ ] Store is instantiated and lifecycle-managed by the core engine
- [ ] Store is injected into each module at registration time alongside the `StubRegistrar`
- [ ] Store is scoped by service ID — modules write and read under their own prefix
- [ ] Store supports `put`, `get`, `list`, and `clear` operations
- [ ] `clear()` with no arguments clears all state across all services
- [ ] `clear(serviceId)` clears state for a single service only
- [ ] Store has no dependency on WireMock types
- [ ] Store is persistent — state survives a CloudMock restart
- [ ] Store is thread-safe
- [ ] Core exposes the store to the admin REST API for read queries

## Dependencies

- [0024](0024-design-state-store-interface.md) (state store design — interface sketch and SPI injection point)
- 0003 (core engine)

## Notes

- The store must be persistent. State surviving a restart is a hard requirement — downstream features depend on it.
- Thread safety matters — the AWS SDK may fire concurrent requests and multiple modules write to the same
  store instance.
- The admin REST API, CLI, and console read from the store but never write directly. All writes go through
  module code triggered by AWS SDK calls.

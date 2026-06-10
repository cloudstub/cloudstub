# Pluggable, efficient persistence backend for the state store

**Type:** core

## Summary

`JsonFileStateStore` (the persistent `StateStore` implementation from
[0035](0035-implement-state-store.md)) serialises the **entire** state map to disk on every single
mutation — `put`, `delete`, `clear`, `clearAll` each call `flush()`, which writes the whole document
to a temp file and atomically renames it. That makes a single write O(n) in the size of the store,
and a burst of M writes O(M²) in bytes written. The new stateful SQS module turns ordinary usage
(e.g. sending many messages) into exactly that burst pattern.

For local-dev scale this is functional, but persistence is currently a checkbox rather than a
designed store. [0024](0024-design-state-store-interface.md) anticipated this: it deliberately made
`StateStore` an interface so the backend could be swapped, and named "embedded file store, SQLite,
etc." as an open implementation choice. This issue makes the persistence backend pluggable and
provides an efficient embedded option so write cost scales with the change, not the whole store.

## Acceptance criteria

- [x] A single mutation no longer rewrites the entire persisted document; write cost scales with the
  size of the change, not the size of the store
- [x] A burst of M writes is not O(M²) in I/O
- [x] The persistence backend is selectable behind the `StateStore` interface (the in-memory store and
  the current file store remain valid choices; a new efficient embedded backend is added)
- [x] State still survives a CloudMock restart (the hard requirement from 0024/0035) and remains
  thread-safe under concurrent writes
- [x] Type fidelity is preserved — a value stored as a concrete type reads back as that type after a
  restart, as `JsonFileStateStore` does today
- [x] No change to the public SPI or to any WireMock type; modules and the admin API are unaffected
- [x] `StateStoreTest` (and the SQS persistence test from 0044) pass against the new backend

## Dependencies

- [0024](0024-design-state-store-interface.md) (state store design — flagged the backend as a future
  choice)
- [0035](0035-implement-state-store.md) (current `JsonFileStateStore` implementation this replaces or
  augments)

## Notes

- Candidate backends: SQLite (embedded, indexed, transactional), an append-only log with periodic
  compaction, or an embedded KV store (e.g. MapDB). Selection is part of this issue.
- Persistence here targets local-development durability, not production scale — the goal is to remove
  the O(n²) write pattern, not to build a database.
- Consider keeping a simple file/in-memory backend as the zero-dependency default so lightweight
  embedded use does not pull in a persistence engine.

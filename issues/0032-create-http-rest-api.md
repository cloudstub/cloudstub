# Admin REST API

**Type:** core

## Summary

Both the CLI and the future management console need an HTTP interface to query and manipulate CloudMock state. The API
is served by standalone mode on a secondary port. Modules register their own admin routes through the same SPI pattern
used for stub registration — adding a module to the classpath automatically exposes its endpoints, removing it
automatically removes them. The API core is a generic router with no service-specific knowledge.

## Route structure

The core provides only global routes:

- `GET /api/status` — running instance info, port, uptime, loaded modules and their registered routes
- `POST /api/reset` — clear all state
- `POST /api/reset?service=<serviceId>` — clear state for a single service
- `GET /api/history` — all captured requests
- `GET /api/history?service=<serviceId>` — filtered by service

Module routes follow the pattern `/api/<serviceId>/<resource>` and are defined entirely by the module. The API core
discovers them at startup through the SPI — the same way it discovers stubs today. If a module JAR is not on the
classpath, its routes do not exist.

## Acceptance criteria

- [ ] API served on a configurable secondary port in standalone mode
- [ ] Core global routes (status, reset, history) are implemented
- [ ] Modules register their own routes via the SPI — no service-specific code in the API core
- [ ] Removing a module from the classpath removes its routes with no other changes
- [ ] `GET /api/status` returns all registered module routes so consumers can discover available operations
- [ ] Responses are JSON
- [ ] API has no dependency on WireMock types — it consumes the state store interface only
- [ ] OpenAPI spec is auto-generated from registered routes

## Dependencies

- 0021 (standalone mode)
- 0024 (state store interface)

## Notes

- The route registration extends the existing `CloudMockService` SPI or introduces a companion interface that
  modules optionally implement. Modules without stateful resources simply register no routes.
- The CLI and management console both consume this API. `GET /api/status` is the discovery endpoint — consumers
  read it to know what services and operations are available rather than maintaining hardcoded lists.
- The secondary port keeps admin traffic separate from AWS SDK traffic.
- Request history should have a configurable size limit to avoid unbounded memory growth.
- Auto-generated OpenAPI spec from registered routes keeps documentation in sync with the actual API surface.

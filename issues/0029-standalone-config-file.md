# Add a config file for standalone mode

**Phase:** 3
**Type:** feature

## Summary

Standalone mode is currently configured only through CLI arguments (`--port`, `--modules`) and environment variables
(`CLOUDMOCK_PORT`, `CLOUDMOCK_MODULES`). As the number of options grows (port, enabled modules, and later fault presets,
a state store, logging), passing everything on the command line becomes unwieldy. Add support for an optional
configuration file — in the spirit of Spring Boot's `application.yml` — that the standalone launcher reads at startup.
This makes a developer's local CloudMock setup reproducible and checkable into version control.

## Acceptance criteria

- [ ] The standalone launcher loads an optional config file (e.g. `cloudmock.yml` / `cloudmock.properties`) from the
  working directory, or from a path given by `--config=<path>`
- [ ] At minimum the file can configure the bound port and the enabled module list (the two options that exist today)
- [ ] Resolution precedence is explicit and documented: CLI flag → environment variable → config file → built-in default
  (CLI overrides everything, mirroring Spring Boot's externalized-config ordering)
- [ ] Absence of a config file is not an error — the server starts on defaults exactly as it does today
- [ ] A malformed config file fails fast with a clear message naming the file and the offending key, not a stack trace
- [ ] Parsing logic lives in a dedicated class (consistent with `PortResolver` / `ModuleSelector`); the launcher stays thin
- [ ] No new dependency leaks onto consumers — any YAML/properties parser is confined to `cloudmock-standalone`
- [ ] At least one test loads a sample config file and asserts the resolved port and module set
- [ ] `docs/standalone.md` and CLAUDE.md document the file format, location, keys, and precedence

## Dependencies

- #0021 (standalone mode — provides the launcher, `PortResolver`, and `ModuleSelector` this builds on)

## Notes

- Keep the precedence identical to the existing flag-over-env ordering so behaviour stays predictable: a CLI flag must
  still win over the file, and the file must win over nothing (defaults).
- Format choice (YAML vs `.properties`) is open. YAML reads closer to `application.yml` but pulls in a parser; a flat
  `.properties` file needs no extra dependency. Weigh the dependency cost against ergonomics during design.
- This is purely a standalone-mode convenience. The embedded (JUnit) path is configured in code via the `CloudMock`
  builder and is unaffected.
- Future options that would naturally live in this file: fault-injection presets, a state-store backend selection
  (see #0024), and log verbosity. Design the key namespace with room to grow (e.g. `cloudmock.port`,
  `cloudmock.modules`, `cloudmock.faults.*`).

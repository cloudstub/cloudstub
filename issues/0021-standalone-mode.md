# Add standalone mode

**Phase:** 3
**Type:** feature

## Summary

CloudMock currently only runs embedded inside JUnit tests. For local development workflows, teams need a long-lived
process that boots CloudMock on a configurable port and stays alive — the same role LocalStack plays today. Add a
standalone mode: a runnable JAR or CLI entry point that starts the core engine on a user-specified port, loads all
modules via ServiceLoader exactly as the embedded mode does, and keeps the process alive until interrupted. No JUnit
dependency is required in this mode.

## Acceptance criteria

- [ ] A new `cloudmock-standalone` subproject (or a `main` entry point in `cloudmock-core`) provides a `main` method that boots CloudMock and blocks until the process is stopped
- [ ] The port is configurable via a CLI argument or environment variable; it defaults to `4566` to ease migration from LocalStack
- [ ] Module discovery uses `ServiceLoader.load(CloudMockService.class)` — the same mechanism as the embedded mode; no new registration API is introduced
- [ ] `./gradlew :cloudmock-standalone:shadowJar` (or equivalent) produces a runnable fat JAR
- [ ] Running `java -jar cloudmock-standalone.jar` prints the bound port and "CloudMock started" (or similar) to stdout
- [ ] A `Ctrl-C` / `SIGTERM` shuts the process down cleanly without a stack trace
- [ ] At least one integration test starts the standalone JAR as a subprocess, sends a request using the AWS SDK v2, and asserts it is served correctly
- [ ] CLAUDE.md is updated to document the standalone mode entry point and default port

## Dependencies

- #0003 (core engine must support external port configuration)

## Notes

- The standalone mode must not pull JUnit onto the classpath. If the entry point lives in `cloudmock-core`, gate the
  `main` method behind a separate source set or a thin launcher module.
- The default port `4566` matches LocalStack's default, making it a drop-in for teams using `AWS_ENDPOINT_URL=http://localhost:4566` in their local dev scripts.
- Log output should include which modules were discovered and registered at startup — this is the first thing a user
  checks when a stub is not being served.
- See the standalone mode project memory for prior design notes on this feature.

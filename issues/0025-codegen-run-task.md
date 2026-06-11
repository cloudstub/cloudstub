# Add Gradle convenience tasks to the monorepo

**Type:** dx

## Summary

Several monorepo workflows require multiple manual steps or knowledge of internal paths. Add
Gradle convenience tasks to cover the most common developer workflows. Each task should work
from the repo root with a single command. The codegen `run` task is the primary deliverable;
the remaining tasks follow the same pattern.

## Status

This issue is split. The first branch (`feature/0025-gradle-convenience-tasks`) delivers the
three tasks that depend only on already-completed work:

- `./gradlew :cloudmock-codegen:run` — **done**
- `./gradlew :cloudmock-codegen:validate` — **done** (added a `--validate` flag to the codegen
  entry point; the task injects it so callers pass only `--model`)
- `./gradlew :cloudmock-standalone:run` — **done** (see the corrected task below)

The remaining tasks (`generateDocs`, `checkCompatibility`, `integrationTest`, and the
`spotlessApply` documentation note) depend on other issues that are not yet complete and are
deferred to a follow-up branch.

## Tasks

### `./gradlew :cloudmock-codegen:run --args="--model <path-or-url> --output <dir>"`
Apply the `application` plugin to `cloudmock-codegen` with `mainClass = 'io.cloudmock.codegen.Main'`
so the tool can be run in one step, while keeping the fat JAR for distribution and CI. Pin
`workingDir` to `rootProject.projectDir` so relative `--model` and `--output` paths resolve
from the repo root, consistent with the `java -jar` invocation.

### `./gradlew :cloudmock-codegen:validate --args="--model <path>"`
Validate a Smithy model without generating output. Useful for module authors who want to
check their model before running full codegen. Implemented by adding a `--validate` flag to the
codegen entry point (`Main`); the `validate` Gradle task injects `--validate` via an
`argumentProvider` so callers pass only `--model`.

### `./gradlew :cloudmock-standalone:run`
Start CloudMock in standalone mode from the monorepo without building a fat JAR first. Apply
the `application` plugin to `cloudmock-standalone` (not `cloudmock-core`) — the standalone entry
point `io.cloudmock.standalone.StandaloneMain` lives in `cloudmock-standalone`, so the plugin
belongs where the main class actually is. Pin `workingDir` to the repo root for consistent path
resolution. (The original issue text named `cloudmock-core`; that was incorrect.)

### `./gradlew generateDocs`
Generate Javadoc across all public modules and output to `docs/api/`. Wires into the MkDocs
site so the API reference is always in sync with the source. Runs as part of the docs
deployment workflow.

### `./gradlew checkCompatibility`
Verify JUnit 5 and JUnit 6 compatibility for `cloudmock-junit` as part of the build. Runs
the extension against both JUnit versions and fails if either is broken.

### `./gradlew integrationTest`
Run only integration tests — tests that boot the full CloudMock engine end to end. Separate
from unit tests so fast unit feedback and slower integration feedback have independent tasks.
CI runs both; local development can run unit tests only during rapid iteration.

### `./gradlew spotlessApply`
Format all Java source files across all modules. Added automatically when the Spotless plugin
is applied — see the Spotless issue. Documented here for completeness.

## Acceptance criteria

- [x] `./gradlew :cloudmock-codegen:run --args="--model <path-or-url> --output <dir>"` generates
  a module identical to the `java -jar` path for the same inputs
- [x] `./gradlew :cloudmock-codegen:validate --args="--model <path>"` validates a Smithy model
  without generating any output
- [x] The Shadow fat JAR (`./gradlew :cloudmock-codegen:shadowJar`) still builds and runs
  exactly as before — both plugins coexist
- [x] `workingDir` is pinned to `rootProject.projectDir` for both codegen and standalone `run` tasks
- [x] `./gradlew :cloudmock-standalone:run` starts CloudMock in standalone mode from the repo root
- [ ] `./gradlew generateDocs` generates Javadoc for all public modules to `docs/api/` _(deferred)_
- [ ] `./gradlew checkCompatibility` verifies JUnit 5 and JUnit 6 compatibility _(deferred)_
- [ ] `./gradlew integrationTest` runs integration tests separately from unit tests _(deferred)_
- [ ] `integrationTest` has its own source set — integration test code is not mixed with unit tests _(deferred)_
- [x] All (in-scope) tasks work from the repo root
- [x] All (in-scope) tasks documented in `CLAUDE.md` under the standard commands block
- [x] `docs/gh-pages/codegen.md` documents `run` as the primary in-repo workflow and retains
  `java -jar` as the standalone/distribution path
- [x] Publishing configuration is unaffected — `cloudmock-codegen` still publishes the shadow JAR

## Dependencies

- 0021 (standalone mode — for `:cloudmock-standalone:run`)
- 0012 (codegen — for `:cloudmock-codegen:run` and `:cloudmock-codegen:validate`)
- Spotless issue (for `./gradlew spotlessApply`)
- JUnit compatibility issue (for `checkCompatibility`)
- Javadoc issue (for `generateDocs`)

## Notes

- **Working-directory footgun:** without pinning `workingDir`, tasks execute with the working
  directory set to the subproject folder. A relative path would resolve under the subproject
  instead of the repo root. Pinning `workingDir = rootProject.projectDir` makes `run` and
  `java -jar` behave identically.
- The Shadow plugin integrates with the `application` plugin — applying both is supported and
  common. No conflict with the existing `shadowJar` configuration is expected.
- `--args` quoting stays slightly verbose (`--args="--model x --output y"`); that is inherent
  to Gradle's `JavaExec` argument passing.
- `integrationTest` should have its own source set so integration test code is not mixed
  with unit test code.
- The `generateDocs` task should be wired into the GitHub Actions docs deployment workflow
  so Javadoc is always published alongside the MkDocs site.
- Keep both `run` and `java -jar` invocation paths documented. The fat JAR remains the
  artifact for users outside the monorepo and for CI.

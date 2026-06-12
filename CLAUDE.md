# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git workflow

Every feature must be developed on a dedicated branch created from `main` before any code is written. Branch names
should follow the pattern `feature/<short-description>`. Never commit feature work directly to `main`.

## Documentation style

Documentation — javadoc, inline comments, and reference docs — describes only the actual behavior of the code: what it
does, how to use it, parameters, return values, contracts, thread-safety, and concrete caveats (e.g. what is and is not
simulated). It must not carry narrative: no project history, issue-number storytelling, design-philosophy rationale, or
marketing framing (e.g. "reference implementation", "canonical example", "the lesson is", "promise of permanence").
Inline comments may explain a non-obvious _why_ for a specific line when it prevents a bug, but must not editorialize.
Keep documentation factual and minimal; rationale belongs in commits and issues, not in the code's documentation.

## Current state

The full multi-project Gradle monorepo is in place. The SPI contract is stable and governed by an explicit evolution
policy (see **SPI evolution policy** — it is deliberately never declared closed), the core engine is running, the
`cloudstub-sqs`, `cloudstub-secretsmanager`, `cloudstub-sns`, and `cloudstub-s3` modules are implemented and tested,
the JUnit extension (JUnit 5 and 6) with fault injection is live, the codegen tool exists, and a Spring Boot example application
demonstrates end-to-end usage. A documentation site (MkDocs Material) is built and wired to GitHub Pages.

Work remaining: `cloudstub-dynamodb` and `cloudstub-lambda` module implementations (scaffolding exists), and
additional AWS service modules.

## Build system

Gradle multi-project monorepo. Module isolation is enforced by the root `build.gradle`: no service module may take a
compile or runtime dependency on another service module. CI validates this on every push.

Standard commands:

```
./gradlew build                      # compile + unit-test all subprojects (runs spotlessCheck; not integrationTest)
./gradlew spotlessApply              # reformat all Java sources (run before pushing)
./gradlew spotlessCheck              # fail if any Java source is unformatted
./gradlew :cloudstub-core:test       # single subproject tests
./gradlew integrationTest            # run only integration tests (src/integrationTest; boots the full engine end to end)
./gradlew checkCompatibility         # run cloudstub-junit against both JUnit 5 and JUnit 6 (example suites)
./gradlew generateDocs               # aggregated Javadoc for public modules → docs/api/
./gradlew publishToMavenLocal        # publish for local smoke testing
./gradlew :cloudstub-codegen:run --args="--model <path-or-url> --output <dir>"   # in-repo stub generation (no fat JAR build)
./gradlew :cloudstub-codegen:validate --args="--model <path>"          # validate a Smithy model without generating output
./gradlew :cloudstub-codegen:shadowJar                                 # build the codegen fat JAR (distribution / CI)
java -jar cloudstub-codegen/build/libs/cloudstub-codegen.jar --model <path-or-url> [--output <dir>] [--validate]  # stub generation
./gradlew :cloudstub-standalone:run --args="--services=sqs,sns,secretsmanager,s3"   # start from repo root; copies in-repo module jars into build/modules (no fat JAR build)
./gradlew :cloudstub-standalone:shadowJar                              # build the standalone fat JAR (launcher + core only; no service modules)
java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar --services=sqs   # start on default ports; loads module jars from ./modules
java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar --modules-dir=/path/to/modules --services=sqs   # explicit plugin directory
CLOUDSTUB_PORT=4566 CLOUDSTUB_API_PORT=4567 java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar  # ports via env vars
```

### Code formatting

Java sources are formatted with Spotless using Google Java Format's **AOSP** variant (4-space
indentation). The plugin is applied to all subprojects from the root `build.gradle`, so modules need
no per-module configuration. Run `./gradlew spotlessApply` before pushing; `spotlessCheck` is wired
into `check`/`build` and CI fails on unformatted sources. Spotless also enforces import ordering,
removes unused imports, trims trailing whitespace, and ensures files end with a newline. Sources
under `**/build/**` and `**/generated/**` (codegen output) are excluded.

Markdown is formatted with Prettier (`.prettierrc.json`), checked by the `Prettier` GitHub Actions
workflow on every push and pull request. Run `npx prettier --write "**/*.md"` to fix locally. The
`issues/` directory and `docs/gh-pages/` (MkDocs Material source, whose admonition syntax a
CommonMark formatter would break) are excluded via `.prettierignore`.

The `clm` / `cloudstub` CLI lives in its own repository (`cloudstub/cloudstub-cli`), not in this
monorepo — see the **CLI** section below.

### Test source sets

Every subproject has two test source sets, configured from the root `build.gradle`:

- `src/test/java` — unit tests, run by `test` and therefore by `check`/`build`. Fast feedback.
- `src/integrationTest/java` — integration tests that boot the full CloudStub engine end to end,
  run by the `integrationTest` task. Its configurations extend the `test` ones (so integration
  tests reuse the test dependency set and are exempt from the inter-module isolation check), but it
  is **not** wired into `check`/`build`, so `./gradlew build` stays unit-test-fast. CI runs
  `./gradlew integrationTest` separately. Running the task by name from the repo root executes every
  module's integration tests. Currently only `cloudstub-standalone` has integration tests — the
  subprocess fat-JAR end-to-end tests; other modules' `integrationTest` tasks are `NO-SOURCE`.

`generateDocs` aggregates Javadoc for the published modules into `docs/api/` (gitignored; built in
CI). The docs deployment workflow runs it and copies the output under the MkDocs site so the API
reference deploys at `/api/`.

### Subprojects

| Module                     | Status           | Notes                                                                                         |
| -------------------------- | ---------------- | --------------------------------------------------------------------------------------------- |
| `cloudstub-core`           | Done             | Shaded fat JAR (WireMock + Jetty bundled, no classpath leakage)                               |
| `cloudstub-junit`          | Done             | `@ExtendWith` + `@RegisterExtension`, fault injection annotations; JUnit 5 and 6              |
| `cloudstub-sns`            | Done             | XML/Form protocol; reference implementation for `registerXmlFormStub`                         |
| `cloudstub-sqs`            | Done             | Stateful reference — JSON/X-Amz-Target; send→receive backed by the state store (#0044)        |
| `cloudstub-secretsmanager` | Done             | Reference impl — JSON/X-Amz-Target protocol                                                   |
| `cloudstub-s3`             | Done             | REST path protocol; generated from real AWS Smithy model                                      |
| `cloudstub-dynamodb`       | Scaffolding only | JSON/X-Amz-Target protocol                                                                    |
| `cloudstub-lambda`         | Scaffolding only | JSON/X-Amz-Target protocol                                                                    |
| `cloudstub-codegen`        | Done             | Smithy → CloudStubService stub generator                                                      |
| `cloudstub-standalone`     | Done             | Runnable fat JAR (launcher + core only); loads module jars from a plugin directory; port 4566 |
| `cloudstub-example:junit6` | Done             | Spring Boot app + integration tests with JUnit 6 (CloudStubExtension)                         |
| `cloudstub-example:junit5` | Done             | Standalone CloudStubExtension tests compiled and run against JUnit 5                          |

The `clm` / `cloudstub` CLI is **not** a subproject of this monorepo — it lives in a separate
repository (`cloudstub/cloudstub-cli`). See the **CLI** section.

### Key dependency versions

- Java 17 LTS minimum
- AWS SDK v2: `2.25.70`
- WireMock: `3.13.1` (shaded inside `cloudstub-core`)
- JUnit: `6.1.0`
- Smithy: `1.50.0`

## Architecture

Three layers, strictly in order of dependency:

1. **`cloudstub-core`** — boots an embedded WireMock server (shaded, invisible to consumers), injects `aws.endpoint-url`
   system property to redirect AWS SDK v2 traffic, runs `ServiceLoader.load(CloudStubService.class)` to discover and
   initialise all installed modules. Published as a fat JAR with WireMock and Jetty relocated to `io.cloudstub.shaded.*`
   so it does not conflict with the user's own Jetty or Spring Boot BOM.

2. **`cloudstub-*` modules** — each is an independently installable JAR that implements the `CloudStubService` SPI and
   registers stubs through the `StubRegistrar` facade. Strict isolation: a module cannot depend on another module.

3. **WireMock (embedded)** — handles all networking, request matching, and Handlebars template processing. Completely
   hidden; no WireMock type is ever exposed in CloudStub's public API.

## Standalone mode

`cloudstub-standalone` is a thin launcher module. Its fat JAR contains only the launcher and `cloudstub-core` (with
its shaded WireMock/Jetty) — **no service modules are bundled**. Service modules are distributed as separate jars and
loaded at runtime from a plugin directory. It is the drop-in replacement for LocalStack in local development scripts.

- **Distribution model:** download the server jar once, then download the module jars you want and drop them in the
  plugin directory. Each module jar is small (the `CloudStubService` class plus its `META-INF/services` registration).
- **Plugin directory:** module jars are loaded from a directory (default `./modules`), overridable with
  `--modules-dir=<path>` CLI argument or `CLOUDSTUB_MODULES_DIR` environment variable. Jars in that directory are
  loaded via a `URLClassLoader` whose parent is the app classloader, so `io.cloudstub.core.spi` types resolve to the
  same classes core uses. An explicitly provided `--modules-dir` that does not exist (or a blank value) fails fast; a
  missing or empty **default** `./modules` is not fatal — the server starts and serves nothing. The resolved plugin
  directory and the loaded modules are printed at startup. `--modules-dir` controls what is _available_ (which jars are
  on the classpath); `--services` (below) narrows what is _enabled_ among those.
- **Default port:** `4566` (matches LocalStack, so `AWS_ENDPOINT_URL=http://localhost:4566` works without changes)
- **Port override:** `--port=<n>` CLI argument or `CLOUDSTUB_PORT` environment variable
- **API port:** `4567` (default) — REST API served on a secondary port in the same process; override with
  `--api-port=<n>` or `CLOUDSTUB_API_PORT`
- **Request history cap:** `--max-history=<n>` CLI argument or `CLOUDSTUB_MAX_HISTORY` env var bounds the in-memory
  request journal exposed by `GET /api/history` (default `1000`; `unlimited`/`none`/`0` to disable). Backed by
  `CloudStub.withMaxRequestHistory(int)`, which sets WireMock's `maxRequestJournalEntries`. A full `POST /api/reset`
  (no `service`) also clears the history via `CloudStub.clearHistory()`; a single-service reset does not.
- **Module discovery:** `ServiceLoader.load(CloudStubService.class, <plugin-classloader>)` over the jars in the plugin
  directory — the same SPI mechanism as embedded mode, only the classloader differs; printed to stdout at startup. The
  launcher sets the thread context classloader to the plugin classloader before `CloudStub.start()` so core's
  `ModuleInitializer` discovers the same module set.
- **Service selection:** `--services=<a,b>` CLI argument or `CLOUDSTUB_SERVICES` env var enables only the listed
  service IDs. Services are **opt-in**: with no selection the server starts but loads nothing, logging an actionable
  warning that names `--services` / `CLOUDSTUB_SERVICES` and the available IDs. This matches embedded mode, where only
  modules on the classpath load. Backed by `CloudStub.withEnabledServices(Collection<String>)` in core, which filters
  `ServiceLoader` discovery (the launcher always passes the resolved set, empty when nothing is selected). Naming an
  unknown service fails fast. Modules added via `withService` bypass the filter.
- **Shutdown:** `Ctrl-C` / `SIGTERM` triggers a clean WireMock shutdown via a JVM shutdown hook, no stack trace
- **State:** standalone and embedded mode share the same core engine and the same state behaviour. Modules built on
  the stateful handler overloads (issue #0044) return live data — e.g. SQS `ReceiveMessage` returns the payloads of
  prior `SendMessage` calls. State is in-memory by default and persistent when a store directory is set (the
  standalone launcher defaults to a persistent `.cloudstub` directory); see the **State store** notes. Template-only
  modules remain stateless. A full `POST /api/reset` clears all state (`StateStore.clearAll()`); `?service=X` clears
  only that service's prefix.
- **Module isolation rule:** `cloudstub-standalone` is **not** exempt from the inter-module isolation check — it
  depends only on `cloudstub-core` at compile/runtime. The in-repo module jars are referenced solely through a
  test-scoped jar-collection configuration (`integrationTestModuleJars`) that is copied into `build/modules` to
  populate a plugin directory for the `run` task and the integration-test subprocess; these are not compile or runtime
  dependencies and do not bundle the modules into the fat JAR.
- **API service filtering:** `StandaloneMain` filters discovered `CloudStubApiService` implementations by the enabled
  service set, so a service not enabled via `--services` exposes neither stubs nor REST routes nor CLI commands

## State store

`StateStore` is the shared, core-owned live-data backend (`io.cloudstub.core.spi.StateStore`). The implementation is
chosen by `StateStoreFactory` and is pluggable behind the interface — modules and the admin API see only the SPI, never
the backend (issue #0047).

- **In-memory** (`InMemoryStateStore`) — default when no store directory is set; state lost on stop. Used in embedded
  test mode.
- **Append-log** (`AppendLogStateStore`) — **default persistent backend.** Records each mutation as a single appended
  line in `{storeDir}/cloudstub-state.log`, so a write costs the size of the change, not the size of the store, and a
  burst of M writes is O(M) bytes rather than O(M²). The log is replayed on startup and periodically compacted (rewritten
  as one `put` per live key via an atomic temp-file rename) to bound its size; a compaction failure is logged and never
  fails the caller's write or breaks the store. A malformed record (truncated mid-append crash, or a structurally-valid
  line missing fields) is skipped rather than aborting startup, matching the JSON store's "corrupt file starts empty"
  guarantee. On first run against a directory that still holds a legacy `cloudstub-state.json` but no log, those entries
  are migrated into the log (and the `.json` renamed to `.json.migrated`) so the default-backend switch loses no state.
  Zero new dependencies — uses the jackson already shaded in core.
- **JSON file** (`JsonFileStateStore`) — legacy persistent backend, retained as an explicit choice. Rewrites the whole
  `cloudstub-state.json` document on every mutation (O(store size) per write); fine for small or static state.

Backend selection: `CloudStub.withPersistenceBackend(StatePersistence.APPEND_LOG | JSON_FILE)`; only relevant when a
store directory is configured via `withStoreDirectory`. All persistent backends preserve value types across a restart
(jackson default typing, configured once in `StateStoreMapper`) and are thread-safe under concurrent writes.

## CLI

`cloudstub-cli` (`clm` / `cloudstub`) is a thin HTTP client over the standalone REST API. It lives in its **own
repository** (`cloudstub/cloudstub-cli`, cloned locally at `../cloudstub-cli`), not in this monorepo — that placement
is intentional and required by issue #0033. The notes below describe how it integrates with this repo's REST API.

- **No dependencies on CloudStub:** depends only on picocli + jackson — _not_ on `cloudstub-core`, WireMock, or any
  service module. That zero-coupling is what lets it be a separate repo: it never imports a CloudStub type, it only
  speaks HTTP to `/api/status` and `/api/<service>/…`.
- **Runtime discovery:** built-in commands are `status` and `reset`; every other command is built at startup from the
  `routes` array of `GET /api/status`. A module route advertising a `service`, `command`, and `params` becomes
  `clm <service> <command>` with one option per param. Adding a module therefore adds CLI commands with no CLI change.
- **Service logic lives server-side:** each module exposes its CLI actions as REST routes via the optional
  `CloudStubApiService` SPI (see below). The CLI never speaks an AWS wire protocol itself.
- **Connection:** `--host` / `--api-port` flags or `CLOUDSTUB_HOST` / `CLOUDSTUB_API_PORT` env vars; talks only to the
  API port. Connection failure → "not running" message + non-zero exit; non-2xx → server's error surfaced, exit 1.

## API service SPI

`CloudStubApiService` is the optional companion to `CloudStubService` for modules that expose REST routes (and thereby
CLI commands) under `/api/<serviceId>/…`. Discovered via `META-INF/services/io.cloudstub.core.spi.CloudStubApiService`.

```java
public interface CloudStubApiService {
    String serviceId();

    void registerRoutes(CloudStubApiContext context);
}

public interface CloudStubApiContext {       // mirrors CloudStubContext on the stub side
    ApiRouteRegistrar registrar();
    StateStore stateStore();                 // the SAME instance the module's stubs use
}

public interface ApiRouteRegistrar {
    // command name + params are surfaced in /api/status and drive the CLI
    void register(HttpMethod method, String path, String command, String description,
                  List<ApiParam> params, ApiHandler handler);

    default void register(HttpMethod method, String path, String description, ApiHandler handler);
}

public record ApiParam(String name, boolean required, String description) {
}
```

Handlers (`ApiRequest -> ApiResponse`) use only core SPI types + the JDK — no WireMock, AWS SDK, jackson, or picocli.
Parameters arrive as query-string values; the request body is not read. Because `registerRoutes` receives the shared
`StateStore` (issue #0049), REST routes are **state-backed**: they read and write the same data as the AWS-protocol
stubs, so a message sent through the AWS SDK is returned by `GET /api/sqs/receive-message` and shown in the console,
and vice versa. This is one state with two representations — the AWS wire protocol on the mock port, a friendly JSON
view on the API port. Modules share the key scheme between their stub and API surfaces (e.g. `SqsKeys`) so the two
cannot drift. Reference impls: `CloudStubSqsApiService`, `CloudStubS3ApiService`, `CloudStubSecretsManagerApiService`
(S3 and Secrets Manager API surfaces are still synthetic pending their own state-backing).

## SPI contract

```java
public interface CloudStubService {
    String serviceId();                    // e.g. "sqs", "secretsmanager"

    void register(CloudStubContext context);
}

public interface CloudStubContext {
    StubRegistrar registrar();             // declare HTTP stubs

    StateStore stateStore();               // shared, core-owned live-data backend
}

public interface StubRegistrar {
    // Template stubs — static Handlebars, stateless
    void registerXmlFormStub(String actionName, String responseTemplate);

    void registerJsonTargetStub(String target, String responseTemplate);

    void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate);

    // Stateful stubs — run a StubHandler at request time with access to the StateStore (issue #0044)
    void registerXmlFormStub(String actionName, StubHandler handler);

    void registerJsonTargetStub(String target, StubHandler handler);

    void registerRestStub(HttpMethod method, String pathPattern, StubHandler handler);
}

@FunctionalInterface
public interface StubHandler {           // (StubRequest, StateStore) -> StubResponse
    StubResponse handle(StubRequest request, StateStore store);
}
```

Modules register themselves via `META-INF/services/io.cloudstub.core.spi.CloudStubService`.

Each protocol comes in two flavours: a **template** overload (static Handlebars, stateless) and a **handler**
overload that runs module Java code per request with access to the shared `StateStore`, so what a user sends in one
call comes back in the next. Handlers receive a `StubRequest` (method/path/body/header/query, plus
`jsonField(path)` to read JSON request-body fields without a per-module parser — no WireMock or JSON-library type)
and return a `StubResponse` (status + content type + body, plus optional headers); internally they are driven by a
single `StatefulResponseTransformer` (a WireMock `ResponseTransformerV2`, keyed by a per-stub handler-key parameter)
so the networking engine stays hidden and fault injection (throttle/timeout/brownout) still applies to handler-based
stubs. Handlers must depend only on the core SPI and the JDK — no WireMock, AWS SDK, jackson, or picocli. The
`jsonField` parsing lives in core (`WireMockStubRequest`, backed by shaded jackson), so the SPI exposes no JSON type
and JSON-protocol modules (SQS, DynamoDB, Lambda) share one parser instead of hand-rolling regex (issue #0045).

No raw WireMock `MappingBuilder` escape hatch will be added: exposing a WireMock type in the public SPI would make it
impossible to swap the underlying HTTP engine without a breaking change. The handler overloads were the sanctioned
extension for stateful routing; any further protocol needs are added the same way — a new `StubRegistrar` method via
the normal issue flow.

## SPI evolution policy

The SPI (`CloudStubService`, `CloudStubContext`, `StubRegistrar`, `StubHandler`, `StateStore`, and the
`CloudStubApiService` family) stays **open to change** — now and after 1.0. It has already changed twice during normal
development (`register(StubRegistrar)` → `register(CloudStubContext)` for the state store; the `StubHandler` overloads
for stateful stubs), and we deliberately keep the freedom to change it again whenever a better design appears rather
than locking ourselves out of one. The question is never _whether_ the contract may change but _how_ a change is
managed:

- **Additive changes** (new methods, new overloads, new interfaces, new `default` methods) are routine. They go in via
  the normal issue flow and require only a minor version bump of `cloudstub-core`. The `StubHandler` overloads are the
  canonical example: existing modules kept compiling and running unchanged.
- **Breaking changes** (removing or re-signing a method, changing semantics) are allowed but require a major version
  bump of `cloudstub-core` and a migration note. They are not forbidden — they are simply priced.
- **Compatibility is enforced by version, not by promise.** Each module JAR declares the minimum core it needs via the
  `CloudStub-Core-Min-Version` entry in its `MANIFEST.MF`; the core reads this attribute at startup and warns when the
  running core is older. This is the mechanism that lets the SPI stay open to change safely: a module built against a
  newer additive method states its floor, and an out-of-date core is detected rather than failing obscurely. Core and
  module JARs therefore version independently — a module pins only its _minimum_ core, not an exact one.

The result is stability through versioning and a clear additive-vs-breaking line.

## Definition of done for infrastructure issues

An infrastructure issue (a store, a transformer, a registrar, any shared plumbing) is **not done when the plumbing
exists** — it is done when a real consumer exercises it end to end through the running system. A module on the request
path, the admin API, or an end-to-end test must read and write the new infrastructure for the issue to close; building
the mechanism, wiring its lifecycle, and exposing it to callers is necessary but not sufficient. This rule exists
because [0035](issues/0035-implement-state-store.md) shipped a complete, lifecycle-managed, persistent, thread-safe
state store that **nothing on the request path actually read or wrote**, so the server still served stateless
responses; the gap was only closed later by [0044](issues/0044-stateful-stub-handlers.md). Acceptance criteria for an
infra issue must therefore name the consumer that proves it works, not just the artifact that was built.

## Request routing protocols

AWS SDK v2 uses JSON/X-Amz-Target for SQS (confirmed in implementation — the CLAUDE.md table was outdated).
All three `StubRegistrar` routing methods are now exercised by real modules: JSON/X-Amz-Target by `cloudstub-sqs` and
`cloudstub-secretsmanager`, XML/Form by `cloudstub-sns`, and REST path by `cloudstub-s3`.

| Protocol            | Services                       | Matching rule                |
| ------------------- | ------------------------------ | ---------------------------- |
| JSON / X-Amz-Target | SQS, Secrets Manager, DynamoDB | `X-Amz-Target` header        |
| XML / Form URL      | SNS (legacy)                   | `Action` form body parameter |
| REST path           | S3                             | HTTP method + path regex     |

Response templates use Handlebars. Available helpers:

- `{{randomValue type='UUID'}}` — fresh UUID per request (WireMock built-in)
- `{{jsonPath request.body '$.Field'}}` — extract field from JSON body (WireMock built-in)
- `{{md5 '...'}}` — MD5 hex digest (CloudStub custom helper, used for SQS checksums)

## Fault injection

Three annotations in `cloudstub-junit`, applied at test-method level:

- `@SimulateThrottle(service = "sqs")` — HTTP 400 ThrottlingException
- `@SimulateTimeout(service = "sqs")` — 30 s server-side delay
- `@SimulateNetworkBrownout(service = "sqs", rate = 0.5)` — connection reset at given rate

`CloudStubExtension` clears all faults after every test method automatically.

## Spring Boot integration note

`cloudstub-core` shades WireMock and Jetty internally. Users can freely use
`platform('org.springframework.boot:spring-boot-dependencies:...')` without any Jetty version conflict.

Inside the monorepo, `cloudstub-example` uses explicit Spring Boot versions (not the BOM) because project-path
dependencies bypass the shadow JAR. This is a development-only constraint — published artifacts have no such limitation.

## Open questions

_None open._ The former question — versioning and compatibility policy between `cloudstub-core` versions and module JAR
versions — is resolved by the **SPI evolution policy** above: modules version independently and pin only a minimum core
via `CloudStub-Core-Min-Version`; additive changes bump the minor, breaking changes bump the major.

## Out of scope

CloudStub does not simulate: SQS FIFO deduplication/ordering, S3 multipart upload lifecycle, DynamoDB conditional
expressions/transactions, IAM policy evaluation. Tests requiring these semantics should use LocalStack or real AWS.

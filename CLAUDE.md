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
Inline comments may explain a non-obvious *why* for a specific line when it prevents a bug, but must not editorialize.
Keep documentation factual and minimal; rationale belongs in commits and issues, not in the code's documentation.

## Current state

The full multi-project Gradle monorepo is in place. The SPI contract is stable and governed by an explicit evolution
policy (see **SPI evolution policy** — it is deliberately never declared closed), the core engine is running, the
`cloudmock-sqs`, `cloudmock-secretsmanager`, `cloudmock-sns`, and `cloudmock-s3` modules are implemented and tested,
the JUnit 6 extension with fault injection is live, the codegen tool exists, and a Spring Boot example application
demonstrates end-to-end usage. A documentation site (MkDocs Material) is built and wired to GitHub Pages.

Work remaining: `cloudmock-dynamodb` and `cloudmock-lambda` module implementations (scaffolding exists), and
additional AWS service modules.

## Build system

Gradle multi-project monorepo. Module isolation is enforced by the root `build.gradle`: no service module may take a
compile or runtime dependency on another service module. CI validates this on every push.

Standard commands:

```
./gradlew build                      # compile + test all subprojects
./gradlew :cloudmock-core:test       # single subproject tests
./gradlew publishToMavenLocal        # publish for local smoke testing
./gradlew :cloudmock-codegen:shadowJar                                 # build the codegen fat JAR
java -jar cloudmock-codegen/build/libs/cloudmock-codegen.jar --model <path-or-url> [--output <dir>]  # stub generation
./gradlew :cloudmock-standalone:shadowJar                              # build the standalone fat JAR
java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar    # start on default ports (4566 mock, 4567 API)
java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar --port=4566 --api-port=4567   # explicit ports
CLOUDMOCK_PORT=4566 CLOUDMOCK_API_PORT=4567 java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar  # via env vars
```

The `clm` / `cloudmock` CLI lives in its own repository (`cloud-mock/cloudmock-cli`), not in this
monorepo — see the **CLI** section below.

### Subprojects

| Module                     | Status           | Notes                                                                                  |
|----------------------------|------------------|----------------------------------------------------------------------------------------|
| `cloudmock-core`           | Done             | Shaded fat JAR (WireMock + Jetty bundled, no classpath leakage)                        |
| `cloudmock-junit6`         | Done             | `@ExtendWith` + `@RegisterExtension`, fault injection annotations                      |
| `cloudmock-sns`            | Done             | XML/Form protocol; reference implementation for `registerXmlFormStub`                  |
| `cloudmock-sqs`            | Done             | Stateful reference — JSON/X-Amz-Target; send→receive backed by the state store (#0044) |
| `cloudmock-secretsmanager` | Done             | Reference impl — JSON/X-Amz-Target protocol                                            |
| `cloudmock-s3`             | Done             | REST path protocol; generated from real AWS Smithy model                               |
| `cloudmock-dynamodb`       | Scaffolding only | JSON/X-Amz-Target protocol                                                             |
| `cloudmock-lambda`         | Scaffolding only | JSON/X-Amz-Target protocol                                                             |
| `cloudmock-codegen`        | Done             | Smithy → CloudMockService stub generator                                               |
| `cloudmock-standalone`     | Done             | Runnable fat JAR; boots all service modules on port 4566 (default) for local dev       |
| `cloudmock-example`        | Done             | Spring Boot app + integration tests (CloudMockExtension)                               |

The `clm` / `cloudmock` CLI is **not** a subproject of this monorepo — it lives in a separate
repository (`cloud-mock/cloudmock-cli`). See the **CLI** section.

### Key dependency versions

- Java 17 LTS minimum
- AWS SDK v2: `2.25.70`
- WireMock: `3.13.1` (shaded inside `cloudmock-core`)
- JUnit: `6.1.0`
- Smithy: `1.50.0`

## Architecture

Three layers, strictly in order of dependency:

1. **`cloudmock-core`** — boots an embedded WireMock server (shaded, invisible to consumers), injects `aws.endpoint-url`
   system property to redirect AWS SDK v2 traffic, runs `ServiceLoader.load(CloudMockService.class)` to discover and
   initialise all installed modules. Published as a fat JAR with WireMock and Jetty relocated to `io.cloudmock.shaded.*`
   so it does not conflict with the user's own Jetty or Spring Boot BOM.

2. **`cloudmock-*` modules** — each is an independently installable JAR that implements the `CloudMockService` SPI and
   registers stubs through the `StubRegistrar` facade. Strict isolation: a module cannot depend on another module.

3. **WireMock (embedded)** — handles all networking, request matching, and Handlebars template processing. Completely
   hidden; no WireMock type is ever exposed in CloudMock's public API.

## Standalone mode

`cloudmock-standalone` is a thin launcher module that bundles `cloudmock-core` and all current service modules into a
single runnable fat JAR. It is the drop-in replacement for LocalStack in local development scripts.

- **Default port:** `4566` (matches LocalStack, so `AWS_ENDPOINT_URL=http://localhost:4566` works without changes)
- **Port override:** `--port=<n>` CLI argument or `CLOUDMOCK_PORT` environment variable
- **API port:** `4567` (default) — REST API served on a secondary port in the same process; override with
  `--api-port=<n>` or `CLOUDMOCK_API_PORT`
- **Request history cap:** `--max-history=<n>` CLI argument or `CLOUDMOCK_MAX_HISTORY` env var bounds the in-memory
  request journal exposed by `GET /api/history` (default `1000`; `unlimited`/`none`/`0` to disable). Backed by
  `CloudMock.withMaxRequestHistory(int)`, which sets WireMock's `maxRequestJournalEntries`. A full `POST /api/reset`
  (no `service`) also clears the history via `CloudMock.clearHistory()`; a single-service reset does not.
- **Module discovery:** `ServiceLoader` — the same mechanism as embedded mode; printed to stdout at startup
- **Module selection:** `--modules=<a,b>` CLI argument or `CLOUDMOCK_MODULES` env var enables only the listed service
  IDs (default: all bundled modules). Backed by `CloudMock.withEnabledServices(Collection<String>)` in core, which
  filters `ServiceLoader` discovery. Naming an unknown module fails fast. Modules added via `withService` bypass the
  filter.
- **Shutdown:** `Ctrl-C` / `SIGTERM` triggers a clean WireMock shutdown via a JVM shutdown hook, no stack trace
- **State:** standalone and embedded mode share the same core engine and the same state behaviour. Modules built on
  the stateful handler overloads (issue #0044) return live data — e.g. SQS `ReceiveMessage` returns the payloads of
  prior `SendMessage` calls. State is in-memory by default and persistent when a store directory is set (the
  standalone launcher defaults to a persistent `.cloudmock` directory); see the **State store** notes. Template-only
  modules remain stateless. A full `POST /api/reset` clears all state (`StateStore.clearAll()`); `?service=X` clears
  only that service's prefix.
- **Module isolation rule:** `cloudmock-standalone` is exempt from the inter-module isolation check in `build.gradle`
  because its purpose is to bundle all modules; this exemption is intentional and must not be extended to other modules
- **API service filtering:** `StandaloneMain` filters discovered `CloudMockApiService` implementations by the enabled
  module set, so a service disabled with `--modules` exposes neither stubs nor REST routes nor CLI commands

## State store

`StateStore` is the shared, core-owned live-data backend (`io.cloudmock.core.spi.StateStore`). The implementation is
chosen by `StateStoreFactory` and is pluggable behind the interface — modules and the admin API see only the SPI, never
the backend (issue #0047).

- **In-memory** (`InMemoryStateStore`) — default when no store directory is set; state lost on stop. Used in embedded
  test mode.
- **Append-log** (`AppendLogStateStore`) — **default persistent backend.** Records each mutation as a single appended
  line in `{storeDir}/cloudmock-state.log`, so a write costs the size of the change, not the size of the store, and a
  burst of M writes is O(M) bytes rather than O(M²). The log is replayed on startup and periodically compacted (rewritten
  as one `put` per live key via an atomic temp-file rename) to bound its size; a compaction failure is logged and never
  fails the caller's write or breaks the store. A malformed record (truncated mid-append crash, or a structurally-valid
  line missing fields) is skipped rather than aborting startup, matching the JSON store's "corrupt file starts empty"
  guarantee. On first run against a directory that still holds a legacy `cloudmock-state.json` but no log, those entries
  are migrated into the log (and the `.json` renamed to `.json.migrated`) so the default-backend switch loses no state.
  Zero new dependencies — uses the jackson already shaded in core.
- **JSON file** (`JsonFileStateStore`) — legacy persistent backend, retained as an explicit choice. Rewrites the whole
  `cloudmock-state.json` document on every mutation (O(store size) per write); fine for small or static state.

Backend selection: `CloudMock.withPersistenceBackend(StatePersistence.APPEND_LOG | JSON_FILE)`; only relevant when a
store directory is configured via `withStoreDirectory`. All persistent backends preserve value types across a restart
(jackson default typing, configured once in `StateStoreMapper`) and are thread-safe under concurrent writes.

## CLI

`cloudmock-cli` (`clm` / `cloudmock`) is a thin HTTP client over the standalone REST API. It lives in its **own
repository** (`cloud-mock/cloudmock-cli`, cloned locally at `../cloudmock-cli`), not in this monorepo — that placement
is intentional and required by issue #0033. The notes below describe how it integrates with this repo's REST API.

- **No dependencies on CloudMock:** depends only on picocli + jackson — *not* on `cloudmock-core`, WireMock, or any
  service module. That zero-coupling is what lets it be a separate repo: it never imports a CloudMock type, it only
  speaks HTTP to `/api/status` and `/api/<service>/…`.
- **Runtime discovery:** built-in commands are `status` and `reset`; every other command is built at startup from the
  `routes` array of `GET /api/status`. A module route advertising a `service`, `command`, and `params` becomes
  `clm <service> <command>` with one option per param. Adding a module therefore adds CLI commands with no CLI change.
- **Service logic lives server-side:** each module exposes its CLI actions as REST routes via the optional
  `CloudMockApiService` SPI (see below). The CLI never speaks an AWS wire protocol itself.
- **Connection:** `--host` / `--api-port` flags or `CLOUDMOCK_HOST` / `CLOUDMOCK_API_PORT` env vars; talks only to the
  API port. Connection failure → "not running" message + non-zero exit; non-2xx → server's error surfaced, exit 1.

## API service SPI

`CloudMockApiService` is the optional companion to `CloudMockService` for modules that expose REST routes (and thereby
CLI commands) under `/api/<serviceId>/…`. Discovered via `META-INF/services/io.cloudmock.core.spi.CloudMockApiService`.

```java
public interface CloudMockApiService {
    String serviceId();

    void registerRoutes(CloudMockApiContext context);
}

public interface CloudMockApiContext {       // mirrors CloudMockContext on the stub side
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
cannot drift. Reference impls: `CloudMockSqsApiService`, `CloudMockS3ApiService`, `CloudMockSecretsManagerApiService`
(S3 and Secrets Manager API surfaces are still synthetic pending their own state-backing).

## SPI contract

```java
public interface CloudMockService {
    String serviceId();                    // e.g. "sqs", "secretsmanager"

    void register(CloudMockContext context);
}

public interface CloudMockContext {
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

Modules register themselves via `META-INF/services/io.cloudmock.core.spi.CloudMockService`.

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

The SPI (`CloudMockService`, `CloudMockContext`, `StubRegistrar`, `StubHandler`, `StateStore`, and the
`CloudMockApiService` family) stays **open to change** — now and after 1.0. It has already changed twice during normal
development (`register(StubRegistrar)` → `register(CloudMockContext)` for the state store; the `StubHandler` overloads
for stateful stubs), and we deliberately keep the freedom to change it again whenever a better design appears rather
than locking ourselves out of one. The question is never *whether* the contract may change but *how* a change is
managed:

- **Additive changes** (new methods, new overloads, new interfaces, new `default` methods) are routine. They go in via
  the normal issue flow and require only a minor version bump of `cloudmock-core`. The `StubHandler` overloads are the
  canonical example: existing modules kept compiling and running unchanged.
- **Breaking changes** (removing or re-signing a method, changing semantics) are allowed but require a major version
  bump of `cloudmock-core` and a migration note. They are not forbidden — they are simply priced.
- **Compatibility is enforced by version, not by promise.** Each module JAR declares the minimum core it needs via the
  `CloudMock-Core-Min-Version` entry in its `MANIFEST.MF`; the core reads this attribute at startup and warns when the
  running core is older. This is the mechanism that lets the SPI stay open to change safely: a module built against a
  newer additive method states its floor, and an out-of-date core is detected rather than failing obscurely. Core and
  module JARs therefore version independently — a module pins only its *minimum* core, not an exact one.

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
All three `StubRegistrar` routing methods are now exercised by real modules: JSON/X-Amz-Target by `cloudmock-sqs` and
`cloudmock-secretsmanager`, XML/Form by `cloudmock-sns`, and REST path by `cloudmock-s3`.

| Protocol            | Services                       | Matching rule                |
|---------------------|--------------------------------|------------------------------|
| JSON / X-Amz-Target | SQS, Secrets Manager, DynamoDB | `X-Amz-Target` header        |
| XML / Form URL      | SNS (legacy)                   | `Action` form body parameter |
| REST path           | S3                             | HTTP method + path regex     |

Response templates use Handlebars. Available helpers:

- `{{randomValue type='UUID'}}` — fresh UUID per request (WireMock built-in)
- `{{jsonPath request.body '$.Field'}}` — extract field from JSON body (WireMock built-in)
- `{{md5 '...'}}` — MD5 hex digest (CloudMock custom helper, used for SQS checksums)

## Fault injection

Three annotations in `cloudmock-junit6`, applied at test-method level:

- `@SimulateThrottle(service = "sqs")` — HTTP 400 ThrottlingException
- `@SimulateTimeout(service = "sqs")` — 30 s server-side delay
- `@SimulateNetworkBrownout(service = "sqs", rate = 0.5)` — connection reset at given rate

`CloudMockExtension` clears all faults after every test method automatically.

## Spring Boot integration note

`cloudmock-core` shades WireMock and Jetty internally. Users can freely use
`platform('org.springframework.boot:spring-boot-dependencies:...')` without any Jetty version conflict.

Inside the monorepo, `cloudmock-example` uses explicit Spring Boot versions (not the BOM) because project-path
dependencies bypass the shadow JAR. This is a development-only constraint — published artifacts have no such limitation.

## Open questions

_None open._ The former question — versioning and compatibility policy between `cloudmock-core` versions and module JAR
versions — is resolved by the **SPI evolution policy** above: modules version independently and pin only a minimum core
via `CloudMock-Core-Min-Version`; additive changes bump the minor, breaking changes bump the major.

## Out of scope

CloudMock does not simulate: SQS FIFO deduplication/ordering, S3 multipart upload lifecycle, DynamoDB conditional
expressions/transactions, IAM policy evaluation. Tests requiring these semantics should use LocalStack or real AWS.

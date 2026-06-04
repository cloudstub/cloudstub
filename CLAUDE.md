# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git workflow

Every feature must be developed on a dedicated branch created from `main` before any code is written. Branch names should follow the pattern `feature/<short-description>`. Never commit feature work directly to `main`.

## Current state

**Phase 2 complete.** The full multi-project Gradle monorepo is in place. The SPI contract is frozen, the core engine is
running, both Phase 2 reference modules are implemented and tested, the JUnit 6 extension with fault injection is live,
the codegen tool exists, and a Spring Boot example application demonstrates end-to-end usage. A documentation site
(MkDocs Material) is built and wired to GitHub Pages.

Phase 3 work remaining: `cloudmock-s3`, `cloudmock-dynamodb`, `cloudmock-lambda` module implementations (scaffolding
exists), and additional AWS service modules (tomorrow's session).

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
```

### Subprojects

| Module | Status | Notes |
|---|---|---|
| `cloudmock-core` | Done | Shaded fat JAR (WireMock + Jetty bundled, no classpath leakage) |
| `cloudmock-junit6` | Done | `@ExtendWith` + `@RegisterExtension`, fault injection annotations |
| `cloudmock-sqs` | Done | Phase 2 reference — JSON/X-Amz-Target protocol |
| `cloudmock-secretsmanager` | Done | Phase 2 reference — JSON/X-Amz-Target protocol |
| `cloudmock-s3` | Scaffolding only | Phase 3 — REST path protocol |
| `cloudmock-dynamodb` | Scaffolding only | Phase 3 — JSON/X-Amz-Target protocol |
| `cloudmock-lambda` | Scaffolding only | Phase 3 — JSON/X-Amz-Target protocol |
| `cloudmock-codegen` | Done | Smithy → CloudMockService stub generator |
| `cloudmock-example` | Done | Spring Boot app + integration tests (CloudMockExtension) |

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

## SPI contract (frozen)

```java
public interface CloudMockService {
    String serviceId();                    // e.g. "sqs", "secretsmanager"
    void register(StubRegistrar registrar);
}

public interface StubRegistrar {
    void registerXmlFormStub(String actionName, String responseTemplate);
    void registerJsonTargetStub(String target, String responseTemplate);
    void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate);
}
```

Modules register themselves via `META-INF/services/io.cloudmock.core.spi.CloudMockService`.

The three `StubRegistrar` methods cover all AWS protocol families in scope. No raw WireMock `MappingBuilder` escape
hatch will be added: exposing a WireMock type in the public SPI would make it impossible to swap the underlying HTTP
engine without a breaking change. If a future service requires routing logic that cannot be expressed through the three
existing methods, the correct path is to add a new method to `StubRegistrar` via the normal issue flow.

## Request routing protocols

AWS SDK v2 uses JSON/X-Amz-Target for SQS (confirmed in implementation — the CLAUDE.md table was outdated).

| Protocol            | Services                          | Matching rule                |
|---------------------|-----------------------------------|------------------------------|
| JSON / X-Amz-Target | SQS, Secrets Manager, DynamoDB    | `X-Amz-Target` header        |
| XML / Form URL      | SNS (legacy)                      | `Action` form body parameter |
| REST path           | S3                                | HTTP method + path regex     |

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

`cloudmock-core` shades WireMock and Jetty internally. Users can freely use `platform('org.springframework.boot:spring-boot-dependencies:...')` without any Jetty version conflict.

Inside the monorepo, `cloudmock-example` uses explicit Spring Boot versions (not the BOM) because project-path
dependencies bypass the shadow JAR. This is a development-only constraint — published artifacts have no such limitation.

## Open questions

1. Versioning and compatibility policy between `cloudmock-core` versions and module JAR versions.

## Out of scope

CloudMock does not simulate: SQS FIFO deduplication/ordering, S3 multipart upload lifecycle, DynamoDB conditional
expressions/transactions, IAM policy evaluation. Tests requiring these semantics should use LocalStack or real AWS.

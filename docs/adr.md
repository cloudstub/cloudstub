# ADR-001: CloudMock — Ultra-Lightweight, Containerless AWS Mock Framework

| Field             | Value               |
|-------------------|---------------------|
| **Status**        | Proposed            |
| **Date**          | 2026-06-01          |
| **Authors**       | CloudMock Core Team |
| **Supersedes**    | —                   |
| **Superseded by** | —                   |

---

## Table of contents

1. [Context](#context)
2. [Problem statement](#problem-statement)
3. [Decision](#decision)
4. [Architecture overview](#architecture-overview)
5. [System layers](#system-layers)
6. [Module design and SPI contract](#module-design-and-spi-contract)
7. [Request routing strategy](#request-routing-strategy)
8. [Agentic AI integration](#agentic-ai-integration)
9. [Implementation roadmap](#implementation-roadmap)
10. [Consequences](#consequences)
11. [Rejected alternatives](#rejected-alternatives)
12. [Open questions](#open-questions)

---

## Context

Modern Java/JVM services depend heavily on AWS managed services — SQS, S3, DynamoDB, Secrets Manager, and others.
Testing these integrations reliably requires either hitting real AWS endpoints (slow, costly, requires credentials) or
running a local mock.

The dominant local mock solution, LocalStack, works by running a full Docker container with a Python-based
reimplementation of AWS services. While comprehensive, this model introduces meaningful friction:

- Container startup adds 15–60 seconds to every CI run.
- Docker must be available in the test environment — a constraint that breaks lightweight CI runners and local setups
  without Docker Desktop.
- The free tier of LocalStack has feature gaps; the Pro tier requires an online license check.
- Every module is always loaded, regardless of which services a project actually uses.

There is no lightweight, in-process, JVM-native alternative with a modular dependency model.

---

## Problem statement

We need a local AWS mock framework that:

- Starts in milliseconds, not seconds, so unit and integration test feedback loops remain fast.
- Runs entirely inside the JVM — no Docker, no Python, no external process.
- Loads only the AWS service modules a project declares as dependencies, keeping the memory footprint proportional to
  actual usage.
- Requires zero configuration, zero credentials, and zero internet connectivity.
- Is open source and free without license verification.

---

## Decision

We will build **CloudMock**: an open-source, in-memory, modular AWS mock framework for the JVM.

The framework uses **WireMock** as an embedded, fully encapsulated networking driver. WireMock is invisible to the
developer — no WireMock APIs are exposed. Service behaviour is delivered through independently installable JAR modules,
each discovered at runtime via Java's native **ServiceLoader** (SPI) mechanism.

---

## Architecture overview

```
┌─────────────────────────────────────────┐
│     Developer application / test suite  │
│     (uses AWS SDK v2, zero code change) │
└──────────────────┬──────────────────────┘
                   │ imports & launches
                   ▼
┌─────────────────────────────────────────┐
│          CloudMock core engine          │
│  • Bootstraps in-memory driver          │
│  • Intercepts SDK endpoint URLs         │
│  • Runs ServiceLoader discovery         │
│  • Zero knowledge of any AWS service    │
└──────────────────┬──────────────────────┘
                   │ registers stubs
                   ▼
┌───────────────────────────────────────────────────────┐
│              Pluggable satellite modules              │
│                                                       │
│  cloudmock-sqs   cloudmock-s3   cloudmock-secrets  …  │
│  (XML/Form)      (REST/binary)  (JSON/target-header)  │
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────┐
│     Underlying networking driver        │
│     (WireMock embedded, encapsulated)   │
└─────────────────────────────────────────┘
```

The AWS SDK is redirected to the local CloudMock port via system property injection (`aws.endpoint-url`). No application
code changes are required.

---

## System layers

### Layer 1 — Orchestration (`cloudmock-core`)

The core engine is responsible for:

- Starting and stopping the embedded WireMock server.
- Injecting the `aws.endpoint-url` system property so AWS SDK v2 clients route traffic locally.
- Running `ServiceLoader.load(CloudMockService.class)` to discover and initialise all installed modules.
- Providing the JUnit 5 `@ExtendWith(CloudMockExtension.class)` lifecycle hook so tests require no manual setup.

The core has **zero compile-time dependencies on any AWS service module** and no knowledge of SQS, S3, DynamoDB, or any
other service.

### Layer 2 — Extension modules (`cloudmock-*`)

Each module is an independently installable JAR. It implements the `CloudMockService` SPI interface and registers its
stubs against the shared WireMock instance during startup.

Modules are strictly isolated: `cloudmock-sqs` cannot depend on `cloudmock-s3`. A project that only needs SQS downloads
only `cloudmock-core` and `cloudmock-sqs`.

### Layer 3 — Driver (WireMock embedded)

WireMock handles all low-level networking, request matching, and Handlebars template processing. It is entirely hidden
behind CloudMock's own APIs. Developers never interact with WireMock directly.

---

## Module design and SPI contract

Every service module must implement the following interface, registered in `META-INF/services/`:

```java
public interface CloudMockService {

    /**
     * Unique service identifier, e.g. "sqs", "s3", "secretsmanager".
     */
    String serviceId();

    /**
     * Called once on startup. The module registers all stubs
     * against the provided registrar.
     */
    void register(StubRegistrar registrar);
}
```

The `StubRegistrar` is a CloudMock-native facade. It exposes stub registration without leaking WireMock types:

```java
public interface StubRegistrar {
    void registerXmlFormStub(String actionName, String responseTemplate);

    void registerJsonTargetStub(String target, String responseTemplate);

    void registerRestStub(HttpMethod method, String pathPattern, String responseTemplate);
}
```

This contract must be **locked before any module is written**. Versioning strategy: semantic versioning on
`cloudmock-core`; modules declare a minimum core version via a manifest attribute.

---

## Request routing strategy

AWS services use two distinct protocol patterns. Each module implements the appropriate one.

### XML / Form URL (e.g. SQS, SNS)

Requests are `application/x-www-form-urlencoded` POST bodies with an `Action` parameter.

```
POST /
Content-Type: application/x-www-form-urlencoded

Action=SendMessage&QueueUrl=...&MessageBody=hello
```

Matching rule: form body parameter `Action` equals the target operation name.

### JSON / X-Amz-Target (e.g. Secrets Manager, DynamoDB)

Requests carry a header identifying the operation.

```
POST /
Content-Type: application/x-amz-json-1.1
X-Amz-Target: secretsmanager.GetSecretValue

{"SecretId": "my-secret"}
```

Matching rule: `X-Amz-Target` header equals `{service}.{OperationName}`.

### REST path (e.g. S3)

Requests are standard HTTP with path-based routing.

```
GET /my-bucket/my-key
```

Matching rule: HTTP method + path regex.

### Dynamic response templating

All response templates use Handlebars to echo back correlation identifiers from the request (e.g. `MessageId`,
`RequestId`). This ensures SDK response parsing does not fail on missing required fields.

---

## Agentic AI integration

CloudMock's clean SPI contract makes it a natural target for an agent layer. Four capabilities are planned:

### Stub generation agent

Takes an AWS Smithy or OpenAPI service model as input and auto-generates a complete CloudMock module: path matchers,
response templates, and SPI registration. Collapses module development from days to minutes.

### Interaction capture agent

A recording proxy mode. The agent observes real AWS SDK calls during a development session, captures request/response
pairs, and synthesises CloudMock stubs from them. Particularly valuable for complex services like Step Functions and
EventBridge where hand-authoring templates is error-prone.

### Test assertion agent

Given a test run against CloudMock, the agent inspects the interaction log and flags correctness issues: a consumer that
never called `DeleteMessage` after processing, a response field asserted by the test but never populated by the stub,
and so on. Turns CloudMock from a passive stub server into an active correctness oracle.

### Fault scenario generator

Given a service topology (e.g. Lambda → SQS → RDS), the agent generates a test matrix of fault scenarios (SQS throttle
mid-flight, RDS timeout after successful dequeue) and produces the corresponding CloudMock fault-injection stubs. This
automates the resilience test coverage that engineers rarely write by hand.

The agent layer sits above `cloudmock-core` and interacts exclusively via the `StubRegistrar` SPI. It has no dependency
on WireMock internals.

---

## Implementation roadmap

### Phase 1 — Monorepo foundation

- Establish Gradle multi-project build with strict inter-module isolation enforced by CI.
- Implement the `CloudMockService` SPI interface and `StubRegistrar` facade — **this contract is frozen before any
  module work begins**.
- Implement `cloudmock-core`: embedded WireMock bootstrap, system property injection, `ServiceLoader` discovery loop.
- Publish to Maven local; validate with a minimal smoke test.

### Phase 2 — Proof-of-concept modules

- `cloudmock-sqs`: establishes the XML/Form routing and Handlebars template standard for all SOAP-style services.
- `cloudmock-secretsmanager`: establishes the JSON/target-header routing standard for all JSON services.
- These two modules serve as the canonical reference implementation for all future module authors.

### Phase 3 — Developer experience

- JUnit 5 `@ExtendWith(CloudMockExtension.class)` — zero-boilerplate test lifecycle management.
- Fault injection API: `@SimulateThrottle`, `@SimulateTimeout`, `@SimulateNetworkBrownout` annotations for resilience
  testing.
- First pass of the stub generation agent (Smithy model → module skeleton).
- Public documentation site with module authoring guide.

---

## Consequences

### Positive

- Test suite startup time drops from 15–60 seconds (LocalStack) to under 100 milliseconds.
- No Docker dependency removes a major CI environment constraint.
- Modular dependency model means unused service code is never on the classpath.
- No license verification — works fully offline and in air-gapped environments.
- The SPI contract enables community-contributed modules without changes to core.

### Negative

- CloudMock is not a full reimplementation of AWS. Complex service behaviours (SQS FIFO deduplication, S3 multipart
  upload lifecycle, IAM policy evaluation) are not simulated. Tests that require these semantics still need LocalStack
  or real AWS.
- Maintaining response template fidelity is ongoing work. AWS API responses are detailed and version-sensitive;
  templates will drift from the real service over time without active maintenance.
- The two-protocol routing strategy (XML/Form vs JSON/target-header) is straightforward for the first two modules but
  will require careful standardisation to stay consistent as the module ecosystem grows.

---

## Rejected alternatives

### LocalStack (container-based)

Rejected due to container overhead, Docker dependency, and online license requirements for the Pro tier. CloudMock is
designed specifically as the lightweight alternative.

### WireMock used directly

Rejected because raw WireMock requires developers to hand-author AWS request matchers and response stubs — significant
boilerplate and deep protocol knowledge per service. CloudMock encapsulates this entirely.

### Mockito / in-process AWS client mocks

Rejected because they mock the Java client, not the HTTP layer. Tests using Mockito mocks do not exercise the SDK's
serialisation, retry logic, or endpoint resolution — the parts most likely to fail in production.

### Testcontainers with LocalStack module

Rejected for the same reasons as LocalStack directly. Testcontainers improves lifecycle management but does not address
the Docker dependency or startup time.

---

## Open questions

| #         | Question                                                                                                     | Owner      | Target  |
|-----------|--------------------------------------------------------------------------------------------------------------|------------|---------|
| ~~1~~     | ~~Should `StubRegistrar` support raw WireMock `MappingBuilder` as an escape hatch for advanced module authors?~~ Resolved: No. See decision record below. | Core team  | Phase 1 |
| ~~2~~     | ~~What is the versioning and compatibility policy between core and module JARs?~~ Resolved: manifest attribute. See decision record below. | Core team  | Phase 1 |
| 3         | Which services should follow SQS and Secrets Manager in Phase 3? (Candidates: S3, DynamoDB, Lambda)          | Community  | Phase 2 |
| 4         | Should the interaction capture agent be a separate Maven/Gradle plugin or bundled in core?                   | Agent team | Phase 3 |
| ~~5~~     | ~~What is the minimum Java version target?~~ Resolved: Java 17 LTS. See decision record below.               | Core team  | Phase 1 |

### Decision: no WireMock escape hatch on StubRegistrar

**Resolved:** `StubRegistrar` exposes no raw WireMock `MappingBuilder` method.

The three routing methods (`registerXmlFormStub`, `registerJsonTargetStub`, `registerRestStub`) cover every AWS service
protocol planned through Phase 3. Exposing `MappingBuilder` would permanently bind the public API to WireMock, making
it impossible to swap the underlying networking driver without a breaking change. If a module author needs behaviour that
`StubRegistrar` cannot express, the correct path is to open an issue requesting a new registration method — not to
reach through to WireMock directly.

If a genuine escape hatch becomes necessary in a future phase, it will be introduced as a separate, clearly marked
interface with an explicit "not covered by stability guarantees" contract, and it will be gated behind an opt-in that
keeps the default API surface clean.

---

### Decision: module compatibility via MANIFEST.MF attribute

**Resolved:** Module JARs declare the minimum compatible `cloudmock-core` version using the `CloudMock-Core-Min-Version`
entry in `MANIFEST.MF`. The core engine reads this attribute for each discovered module at startup and logs a warning if
the running core version is older than the declared minimum.

This approach requires no extra tooling or build machinery: the Gradle `jar` task populates the attribute, and the core
reads it with `getClass().getModule()` / `JarFile` at runtime. `cloudmock-core` itself follows semantic versioning;
any change to the `StubRegistrar` or `CloudMockService` interface constitutes a breaking change and requires a major
version bump.

Module authors should set `CloudMock-Core-Min-Version` to the oldest core release that contains all `StubRegistrar`
methods their module calls. Consumers manage transitive version resolution through their own build tool as usual.

---

### Decision: minimum Java version is Java 17 LTS

**Resolved:** Java 17 LTS.

CloudMock is a library that runs inside the consumer's JVM. The minimum version is not a constraint on CloudMock's own development environment — it is a constraint on every project that wants to adopt CloudMock. Setting the floor too high at launch would exclude a significant portion of the target audience before the project has established itself.

Java 17 remains the dominant LTS in enterprise and Spring Boot 3.x ecosystems as of the time this decision was made, even though Java 21 is the current LTS. None of the features introduced in Java 21 — virtual threads, finalised pattern matching for switch, sequenced collections — are meaningful for CloudMock's core implementation: an embedded WireMock server, a `ServiceLoader` loop, and Handlebars response templates. The upgrade would carry adoption cost with no technical benefit at this stage.

**Build target:** compile against Java 17; run CI against both Java 17 and Java 21 to validate compatibility. When Java 17 adoption in the target ecosystem drops below a meaningful threshold, bumping the minimum is a one-line change in the root `build.gradle`.

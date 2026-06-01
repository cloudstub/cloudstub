# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

Pre-Phase 1. Only `src/Main.java` (Hello World) exists. No build file, no git history. The next step is setting up a Gradle multi-project monorepo.

## Build system

Use **Gradle** (not Maven) with a multi-project layout. Module isolation must be enforced: no module may take a transitive compile dependency on another module. CI must validate this.

Planned subprojects:
- `cloudmock-core` — orchestration engine, SPI interfaces, WireMock bootstrap
- `cloudmock-sqs` — Phase 2 reference module (XML/Form URL protocol)
- `cloudmock-secretsmanager` — Phase 2 reference module (JSON/X-Amz-Target protocol)
- `cloudmock-s3`, `cloudmock-dynamodb`, `cloudmock-lambda` — Phase 3

Target Java 17 LTS minimum.

Once the build is set up, standard commands will be:
```
./gradlew build          # compile + test all subprojects
./gradlew :cloudmock-core:test   # single subproject tests
./gradlew publishToMavenLocal    # publish for local smoke testing
```

## Architecture

Three layers, strictly in order of dependency:

1. **`cloudmock-core`** — boots an embedded WireMock server, injects `aws.endpoint-url` system property to redirect AWS SDK v2 traffic, runs `ServiceLoader.load(CloudMockService.class)` to discover and initialise all installed modules. Zero compile-time knowledge of any AWS service.

2. **`cloudmock-*` modules** — each is an independently installable JAR that implements the `CloudMockService` SPI and registers stubs through the `StubRegistrar` facade. Strict isolation: a module cannot depend on another module.

3. **WireMock (embedded)** — handles all networking, request matching, and Handlebars template processing. Completely hidden; no WireMock type is ever exposed in CloudMock's public API.

## SPI contract (must be frozen before any module is written)

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

Modules register themselves via `META-INF/services/CloudMockService`.

## Request routing protocols

Each module implements exactly one of:

| Protocol | Services | Matching rule |
|---|---|---|
| XML / Form URL | SQS, SNS | `Action` form body parameter |
| JSON / X-Amz-Target | Secrets Manager, DynamoDB | `X-Amz-Target` header |
| REST path | S3 | HTTP method + path regex |

Response templates use Handlebars to echo back correlation identifiers (`MessageId`, `RequestId`, etc.) from the request so the AWS SDK parses responses without error.

## Open questions (resolve during Phase 1)

1. Should `StubRegistrar` expose a raw WireMock `MappingBuilder` escape hatch for advanced module authors?
2. Versioning and compatibility policy between `cloudmock-core` versions and module JAR versions.
3. ~~Minimum Java version~~ — resolved: Java 17 LTS.

## Out of scope

CloudMock does not simulate: SQS FIFO deduplication/ordering, S3 multipart upload lifecycle, DynamoDB conditional expressions/transactions, IAM policy evaluation. Tests requiring these semantics should use LocalStack or real AWS.
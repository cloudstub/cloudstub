# Changelog

All notable changes to CloudStub are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). All published modules share a single
lockstep version.

## [0.1.0-beta.8] - 2026-07-11

Publishes the SSM Parameter Store service module and fixes the codegen tool's model download so
GitHub blob URLs resolve and models land in a project-local directory.

### Added

- `cloudstub-ssm`: new module for AWS Systems Manager Parameter Store. Stateful parameters
  (put/get/get-by-path/delete/describe/history/tags) backed by the state store over the
  JSON/X-Amz-Target protocol. (#118)

### Fixed

- `cloudstub-codegen`: `github.com/.../blob/...` model URLs are rewritten to their raw form before
  fetching, and downloaded models are written to a project-local `build/.cloudstub/codegen/`
  directory instead of a shared temp location. (#210)

## [0.1.0-beta.7] - 2026-07-05

Publishes the DynamoDB and Lambda service modules, disambiguates REST-path routing so S3 and Lambda
can run in one server, and patches dependency vulnerabilities in the shipped and test-path classpaths.

### Added

- `cloudstub-dynamodb`: new module for Amazon DynamoDB. Stateful tables and items
  (put/get/query/scan/update/batch) backed by the state store over the JSON/X-Amz-Target protocol. (#197)
- `cloudstub-lambda`: new module for AWS Lambda. Stateful function lifecycle and tags backed by the
  state store over REST JSON; `Invoke` echoes the request payload. (#200)
- `cloudstub-junit`: `CloudStubExtension.withModules(...)` and `withServices(...)` auto-download and
  enable service modules in embedded tests, matching standalone mode's provisioning. (#195)

### Fixed

- `cloudstub-core`: overlapping REST-path stubs are disambiguated by pattern specificity (the pattern
  with more literal characters wins), so S3's catch-all object patterns and Lambda's API routes no
  longer collide when both modules are loaded in one server. (#199)

### Security

- `cloudstub-core`: the shaded jar now bundles `net.minidev:json-smart` 2.6.0 (GHSA-pq2g-wx69-c263)
  and Jetty 11.0.26 (GHSA-mmxm-8w33-wc4h). Advisories still pinned by WireMock 3 (EOL Jetty 11,
  Handlebars 4.3.x) are tracked for the WireMock 4 migration in #203.
- Bumped the AWS SDK v2 test dependency to 2.46.21 (Netty 4.1.135.Final), clearing the Netty
  advisories from the test and example classpaths. The SDK is not shipped in any published artifact.
- `cloudstub-console`: updated the Angular build toolchain (`npm audit fix`), clearing the Vite,
  esbuild, undici, and Piscina advisories. The toolchain is build-only and not embedded in the
  published console.

## [0.1.0-beta.6] - 2026-06-28

Fixes cloudstub-core's published POM and shadow JAR leaking bundled libraries onto a consumer's
classpath, which aborted startup under Spring Boot 4.

### Fixed

- `cloudstub-core`: the published POM no longer declares the bundled WireMock and Jackson as
  dependencies. Both are relocated inside the shadow JAR, so leaking them transitively pulled
  un-relocated WireMock plus Jetty 11 onto a consumer's classpath. Under Spring Boot 4 (Jetty 12)
  that aborted CloudStub startup with `FatalStartupException: Jetty 11 is not present`. The published
  POM now declares only `slf4j-api` (kept unshaded so the consumer's logging config applies). (#178)
- `cloudstub-core`: the shadow JAR now relocates `com.jayway.jsonpath` alongside WireMock and Jackson.
  It was bundled at its original package, colliding with a consumer's own `json-path` (e.g. the copy
  `spring-boot-starter-test` pulls in). The collided `JacksonJsonProvider` resolved against un-relocated
  Jackson, which Spring Boot 4 (Jackson 3) does not provide, aborting CloudStub startup with
  `NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper`. (#178)

## [0.1.0-beta.5] - 2026-06-24

Fixes S3 uploads from a credentialed client.

### Fixed

- `cloudstub-s3`: `PutObject` from a credentialed `S3Client` (any non-anonymous credentials
  provider) now passes the SDK's checksum validation. A signed client frames the request body as
  `aws-chunked`; the module now strips that chunk framing before storing the object and computing the
  ETag, so the ETag is the MD5 of the actual content. Previously only an `AnonymousCredentialsProvider`
  client (which sends an unframed body) succeeded.

## [0.1.0-beta.4] - 2026-06-22

Publishes three additional service modules.

### Added

- Published `cloudstub-s3`, `cloudstub-sns`, and `cloudstub-secretsmanager` to Maven Central
  (previously only `cloudstub-sqs` shipped among the service modules).
  - `cloudstub-s3`: Amazon S3 over the REST path protocol. Bucket and object operations
    (`PutObject`, `GetObject`, `ListObjectsV2`/`ListObjects`, `DeleteObject`) and object/bucket
    tagging are state-backed: an object put is returned by a later get and listed in the bucket. A
    bundled AWS SDK v2 interceptor rewrites virtual-hosted-style requests aimed at the CloudStub
    endpoint to path-style, so a vanilla `S3Client` works with no `pathStyleAccessEnabled`.
  - `cloudstub-sns`: Amazon SNS topics and subscriptions over the AWS Query (XML/form) protocol.
  - `cloudstub-secretsmanager`: AWS Secrets Manager over the AWS JSON protocol with state-backed
    secret storage: a secret created is returned by a later get.

## [0.1.0-beta.3] - 2026-06-17

Publishes the refactored `cloudstub-sqs` module.

### Changed

- `cloudstub-sqs` regenerated from the AWS SQS Smithy model with full coverage of all 23 operations.
  The eight core queue and message operations remain state-backed: a message sent with `SendMessage`
  is returned by a later `ReceiveMessage`, while the remaining operations return well-formed template
  responses.

## [0.1.0-beta.2] - 2026-06-17

Republished the non-service modules. `cloudstub-sqs` was held at `0.1.0-beta.1` and is unchanged in
this release.

### Added

- `cloudstub-local`: automatic download of declared service modules from Maven Central, verified
  against the strongest published checksum, with the plugin directory acting as the cache.
- `cloudstub-local`: optional `.properties` configuration file for any launcher option, resolved with
  CLI flag → environment variable → file → default precedence.
- `cloudstub-core`: `StubTemplates` helper for loading bundled Handlebars response templates;
  `cloudstub-codegen` emits it for generated modules.

### Changed

- `cloudstub-local`: port and request-history flags tolerate non-numeric values instead of failing.

## [0.1.0-beta.1] - 2026-06-13

First publication to Maven Central under the `io.github.cloudstub` namespace. The `-beta` qualifier
signals that the SPI and public API may still change.

### Added

- Published artifacts: `cloudstub-core`, `cloudstub-testing`, `cloudstub-junit`, `cloudstub-sqs`,
  `cloudstub-sdk-v1`, `cloudstub-local`, `cloudstub-codegen`.
- Embedded mock engine (`cloudstub-core`) that redirects AWS SDK v2 traffic and discovers service
  modules via the `CloudStubService` SPI.
- JUnit 5 and 6 extension (`cloudstub-junit`) with lifecycle management and throttle/timeout/brownout
  fault injection, aggregated for test use by `cloudstub-testing`.
- Stateful Amazon SQS module (`cloudstub-sqs`) backed by the shared state store.
- Standalone server (`cloudstub-local`) and the Smithy stub generator (`cloudstub-codegen`),
  distributed as runnable JARs.

[0.1.0-beta.8]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.7...v0.1.0-beta.8
[0.1.0-beta.7]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.6...v0.1.0-beta.7
[0.1.0-beta.6]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.5...v0.1.0-beta.6
[0.1.0-beta.5]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.4...v0.1.0-beta.5
[0.1.0-beta.4]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.3...v0.1.0-beta.4
[0.1.0-beta.3]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.2...v0.1.0-beta.3
[0.1.0-beta.2]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.1...v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/cloudstub/cloudstub/releases/tag/v0.1.0-beta.1

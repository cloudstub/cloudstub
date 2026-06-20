# Changelog

All notable changes to CloudStub are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). All published modules share a single
lockstep version.

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

[0.1.0-beta.3]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.2...v0.1.0-beta.3
[0.1.0-beta.2]: https://github.com/cloudstub/cloudstub/compare/v0.1.0-beta.1...v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/cloudstub/cloudstub/releases/tag/v0.1.0-beta.1

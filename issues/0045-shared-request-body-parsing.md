# Shared request-body parsing for JSON-protocol modules

**Type:** core

## Summary

Stateful modules must translate an incoming AWS request body into store operations, but the SPI hands
them only a raw `String` (`StubRequest.body()`). As a result `cloudmock-sqs` hand-rolls JSON field
extraction with regex in `SqsJson` (`stringField`, `maxNumberOfMessages`, escape/unescape). Every
JSON-protocol module that follows — `cloudmock-dynamodb`, `cloudmock-lambda`, and any future
JSON/X-Amz-Target service — will reinvent the same parser and re-break the same edge cases (nested
objects, escaped characters, fields whose names are substrings of others, numbers-as-strings).

There is an altitude asymmetry here: the codegen tool already generates **typed response builders**
from the Smithy model (issue [0036](0036-codegen-stateful-response-builders.md)), so the *response*
side is solved once and reused. The *request* side is left to each module. "Read field X out of the
incoming AWS JSON body" is a cross-module need, not an SQS detail, and belongs in core — solved once,
behind an abstraction that does not leak a JSON library type into the public SPI.

## Acceptance criteria

- [x] A core capability lets a `StubHandler` read fields from a JSON request body without writing a
  parser — e.g. `StubRequest.jsonField(String path)` and/or a small `JsonBody` accessor obtained
  from the request
- [x] No JSON-library type (jackson or otherwise) appears in the public SPI signature — `cloudmock-core`
  already shades jackson to `io.cloudmock.shaded.jackson`, so core can parse internally without
  exposing it
- [x] Correctly handles nested objects, JSON string escapes, and fields whose names are prefixes of
  other field names (the cases the current regex approach is fragile around)
- [x] `cloudmock-sqs` is migrated off `SqsJson`'s regex field extraction to the shared accessor
  (`SqsJson` keeps only genuinely SQS-specific helpers, e.g. MD5 and queue-name extraction, or is
  removed if nothing remains)
- [x] The accessor is usable by every JSON/X-Amz-Target module with no per-module parser
- [x] Malformed/truncated input yields a clean result (null/empty/typed error), never an unhandled
  exception out of a handler — preserving the hardening added in [0044](0044-stateful-stub-handlers.md)

## Dependencies

- [0044](0044-stateful-stub-handlers.md) (stateful stub handlers — defines `StubRequest`, the place
  this accessor attaches)
- [0036](0036-codegen-stateful-response-builders.md) (typed response builders — the symmetric
  response-side capability this mirrors for requests)

## Notes

- Scope this to the JSON protocol first (SQS, DynamoDB, Lambda). XML/Form request parsing (SNS) is a
  separate, smaller concern and can be added later through the same accessor pattern.
- A typed, Smithy-aware request *parser* (mirroring the generated response builders) is a larger
  future step; a generic field accessor is the minimum that removes the per-module duplication.
- The store stays AWS-agnostic — this issue is purely about the module's request-side translation
  layer, consistent with the "module is the bridge" model from
  [0024](0024-design-state-store-interface.md).

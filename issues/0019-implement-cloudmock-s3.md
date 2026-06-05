# Implement cloudmock-s3

**Phase:** 3
**Type:** module

## Summary

S3 is the first module to use REST path routing (`registerRestStub`). Until it ships, that entire protocol path is
untested end-to-end — meaning a bug in REST stub matching could go undetected until a later module is built. Scaffolding
for `cloudmock-s3` already exists. Use the codegen tool to generate contract stubs from a Smithy model, then wire up
the module, validate that method and path matching works through the core engine, and ship a working set of S3
operations covered by integration tests.

## Acceptance criteria

- [ ] Run `cloudmock-codegen` against the S3 Smithy model and commit the generated stub output as the starting point
- [ ] `cloudmock-s3` registers stubs for the following operations at minimum: `PutObject`, `GetObject`, `DeleteObject`, `ListObjectsV2`, `CreateBucket`, `HeadObject`
- [ ] Each stub matches on HTTP method and path regex; no operation leaks into another's path pattern
- [ ] Response templates use Handlebars and return well-formed S3 XML responses
- [ ] Integration tests use the AWS SDK v2 S3 client pointed at the CloudMock instance and assert that each supported operation returns without an SDK exception
- [ ] The module registers itself via `META-INF/services/io.cloudmock.core.spi.CloudMockService`
- [ ] `cloudmock-s3` has no compile or runtime dependency on any other `cloudmock-*` module; the Gradle isolation check passes
- [ ] `./gradlew build` passes with the new module included

## Dependencies

- #0003 (core engine — `registerRestStub` must be stable)
- #0012 (codegen tool must be runnable)

## Notes

- S3 uses REST path routing (`HTTP method + path regex`), unlike SQS and Secrets Manager which use `X-Amz-Target`.
  This is the first real exercise of that code path.
- S3 path patterns must handle both path-style (`/bucket/key`) and virtual-hosted-style (`bucket.s3.amazonaws.com/key`)
  if the SDK uses the latter by default; check which style AWS SDK v2 uses when `aws.endpoint-url` is set to localhost.
- Response templates for S3 are XML, not JSON. Handlebars can produce XML — use the existing SQS templates as a
  structural guide but note the content type difference.
- Multipart upload lifecycle is out of scope per CLAUDE.md. A stub that returns a `NotImplemented` error for multipart
  operations is acceptable.

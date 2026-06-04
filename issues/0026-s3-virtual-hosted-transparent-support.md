# Transparent S3 virtual-hosted-style support via SDK interceptor

**Phase:** 3
**Type:** module
**Parent:** #0019 (sub-issue)

## Summary

CloudMock's core promise is that the application under test is written against the **real** AWS SDK
exactly as it would be in production, and CloudMock transparently intercepts the traffic via
`aws.endpoint-url` — the app needs zero awareness that it is being mocked. S3 currently breaks that
promise: the `cloudmock-s3` module routes purely on the URL **path** (`/{bucket}/{key}`), which only
works for path-style requests. A production-faithful S3 client uses **virtual-hosted-style** by default
(`Host: {bucket}.s3…`, or `Host: {bucket}.localhost` against a local endpoint), where the bucket lives
in the host and the path is just `/{key}` — so requests misroute (a `GetObject` looks like a
single-segment path and falls through to the `ListObjects` catch-all).

Close the gap the way CloudMock intends — transparently. Ship an AWS SDK v2 `ExecutionInterceptor` in
`cloudmock-s3` that rewrites virtual-hosted-style S3 requests aimed at the CloudMock endpoint into
path-style before they are sent, so the existing path-regex stubs match. AWS SDK v2 auto-discovers
global interceptors from the classpath; because `cloudmock-s3` is a test-scope dependency, it only
affects the test JVM. The application configures a vanilla `S3Client` — no `pathStyleAccessEnabled`, no
other CloudMock-specific settings. This realizes the #0019 note that "S3 path patterns must handle both
path-style and virtual-hosted-style."

## Acceptance criteria

- [ ] `cloudmock-s3` ships an `ExecutionInterceptor` registered via
      `META-INF/services/software.amazon.awssdk.core.interceptor.ExecutionInterceptor`
- [ ] In `modifyHttpRequest`, a virtual-hosted-style request targeting the CloudMock endpoint
      (`Host: {bucket}.{loopback-host}`) is rewritten to path-style: host → the loopback host,
      path → `/{bucket}` + original path
- [ ] The interceptor only acts on requests to a loopback/CloudMock endpoint; any request to a
      non-local host (a real S3 client in the same JVM) is left untouched
- [ ] Already-path-style requests (`Host: {loopback-host}`, bucket already in the path) pass through
      unchanged
- [ ] Service-level requests with no bucket subdomain (e.g. `ListBuckets`) pass through unchanged
- [ ] A module test proves a **default-configured** `S3Client` — no `pathStyleAccessEnabled`, no
      `checksumValidationEnabled` override — works against CloudMock for the core ops
      (`PutObject`, `GetObject`, `ListObjectsV2`)
- [ ] The existing path-style tests still pass (path-style remains supported alongside virtual-hosted)
- [ ] `./gradlew build` passes

## Dependencies

- #0019 (cloudmock-s3 path-style routing must exist — the interceptor normalizes onto it)

## Notes

- `modifyHttpRequest` runs **after** request signing. CloudMock does not verify signatures, so rewriting
  the URL at that stage is safe (no re-signing needed).
- Keep the loopback guard strict (host ends with `.localhost`, or matches the configured CloudMock
  endpoint host) so a real-S3 client sharing the JVM is never rewritten — this is the safety boundary
  that keeps the interceptor from affecting anything but mocked traffic.
- Verify the **default** client also clears the checksum concern: if request/response checksum
  validation interferes with mock responses, handle it transparently here too rather than pushing
  `checksumValidationEnabled(false)` into consumer code — the whole point is a vanilla app client.
- This interceptor lives in `cloudmock-s3` (not core) because it is S3-protocol-specific; module
  isolation is unaffected (it depends only on the AWS SDK and `cloudmock-core` SPI, not other modules).

# Implement cloudmock-lambda module

**Type:** module

## Summary

Scaffolding for `cloudmock-lambda` already exists. Run the codegen tool against the Lambda Smithy model to
generate contract stubs, then wire up the module and ship a working set of Lambda operations covered by
integration tests.

Lambda uses REST path routing — the same routing path as `cloudmock-s3`. All stubs are registered via
`registerRestStub`. Paths follow the Lambda REST API versioning prefix `/2015-03-31/functions/...`.

## Acceptance criteria

- [ ] Run `cloudmock-codegen` against the Lambda Smithy model and commit the generated stub output as the
  starting point
- [ ] `cloudmock-lambda` registers stubs for the following operations at minimum:
  - `InvokeFunction` (`POST /2015-03-31/functions/{name}/invocations`) → returns HTTP 200 with an empty
    JSON payload; `X-Amz-Function-Error` header absent
  - `ListFunctions` (`GET /2015-03-31/functions/`) → returns `Functions` list
  - `GetFunction` (`GET /2015-03-31/functions/{name}`) → returns `Configuration` with `FunctionName`,
    `FunctionArn`, `Runtime`, `State`
  - `CreateFunction` (`POST /2015-03-31/functions/`) → returns `FunctionName`, `FunctionArn`, `State`
  - `DeleteFunction` (`DELETE /2015-03-31/functions/{name}`) → returns HTTP 204
- [ ] Response templates use Handlebars and return well-formed JSON responses that `LambdaClient`
  (AWS SDK v2) parses without error
- [ ] Integration tests use the AWS SDK v2 Lambda client pointed at the CloudMock instance and assert
  that each supported operation returns without an SDK exception
- [ ] The module registers itself via `META-INF/services/io.cloudmock.core.spi.CloudMockService`
- [ ] `serviceId()` returns `"lambda"`
- [ ] `cloudmock-lambda` has no compile or runtime dependency on any other `cloudmock-*` module; the
  Gradle isolation check passes
- [ ] `./gradlew build` passes with the new module included

## Dependencies

- 0003 (core engine — `registerRestStub` must be stable)
- 0012 (codegen tool)

## Notes

- Stubs are stateless — `InvokeFunction` returns a fixed payload regardless of function configuration.
  Stateful simulation (returning actual invocation results, tracking invocation counts) will be addressed
  separately once the state store (0035) is in place.
- `InvokeFunction` is the highest-priority operation — it is the only one most callers need in tests.
  Ship it first and validate the path matching before moving to the management operations.
- Lambda path patterns must not conflict with each other. The function name segment (`{name}`) should be
  matched with a non-greedy regex that does not swallow the `/invocations` suffix. Use `cloudmock-s3` as
  the reference for REST path pattern discipline.
- `UpdateFunctionCode`, `UpdateFunctionConfiguration`, and async invocation (`InvokeAsync`) are out of
  scope for the initial implementation.

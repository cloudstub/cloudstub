# Implement cloudmock-dynamodb module

**Type:** module

## Summary

Scaffolding for `cloudmock-dynamodb` already exists. Run the codegen tool against the DynamoDB Smithy model to
generate contract stubs, then wire up the module and ship a working set of DynamoDB operations covered by
integration tests.

DynamoDB uses the JSON / `X-Amz-Target` protocol — the same routing path as `cloudmock-sqs` and
`cloudmock-secretsmanager`. All stubs are registered via `registerJsonTargetStub`. Target header values follow
the pattern `DynamoDB_20120810.{OperationName}`.

## Acceptance criteria

- [ ] Run `cloudmock-codegen` against the DynamoDB Smithy model and commit the generated stub output as the
  starting point
- [ ] `cloudmock-dynamodb` registers stubs for the following operations at minimum:
  - `CreateTable` → returns `TableDescription` with `TableName`, `TableStatus`
  - `DeleteTable` → returns `TableDescription`
  - `DescribeTable` → returns `TableDescription`
  - `ListTables` → returns `TableNames` list
  - `PutItem` → returns HTTP 200 with empty attributes
  - `GetItem` → returns `Item` map
  - `DeleteItem` → returns HTTP 200 with empty attributes
  - `UpdateItem` → returns HTTP 200 with empty attributes
  - `Query` → returns `Items` list and `Count`
  - `Scan` → returns `Items` list and `Count`
- [ ] Response templates use Handlebars and return well-formed JSON responses that `DynamoDbClient`
  (AWS SDK v2) parses without error
- [ ] Integration tests use the AWS SDK v2 DynamoDB client pointed at the CloudMock instance and assert
  that each supported operation returns without an SDK exception
- [ ] The module registers itself via `META-INF/services/io.cloudmock.core.spi.CloudMockService`
- [ ] `serviceId()` returns `"dynamodb"`
- [ ] `cloudmock-dynamodb` has no compile or runtime dependency on any other `cloudmock-*` module; the
  Gradle isolation check passes
- [ ] `./gradlew build` passes with the new module included

## Dependencies

- 0003 (core engine)
- 0012 (codegen tool)

## Notes

- Stubs are stateless — `GetItem` returns a synthetic item regardless of any prior `PutItem` call. Stateful
  simulation will be addressed separately once the state store (0035) is in place.
- `BatchWriteItem` and `BatchGetItem` are out of scope for the initial implementation.
- DynamoDB conditional expressions and transactions are out of scope per CLAUDE.md.
- Use `cloudmock-secretsmanager` as the reference implementation — it is the simplest JSON/X-Amz-Target module.

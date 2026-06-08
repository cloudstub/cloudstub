# CLI tool for interacting with CloudMock

**Type:** dx

## Summary

Once standalone mode and the state store exist, developers need a way to inspect and manipulate
mock state from the terminal. The CLI lives in a separate repository `cloud-mock/cloudmock-cli`
and connects to a running CloudMock standalone instance over the REST API. It lets users
list resources, send test data, inspect state, and reset services without writing code or
installing the AWS CLI. The binary ships as `cloudmock` with `clm` as a built-in alias.

## Initial commands

### Global

- `clm status` — show running instance info, port, loaded modules
- `clm reset` — clear all state across all services
- `clm reset --service sqs` — clear state for a single service

### SQS

- `clm sqs list-queues`
- `clm sqs send-message --queue <name> --body <message>`
- `clm sqs receive-message --queue <name>`
- `clm sqs purge-queue --queue <name>`

### S3

- `clm s3 list-buckets`
- `clm s3 list-objects --bucket <name>`
- `clm s3 put-object --bucket <name> --key <key> --body <content>`
- `clm s3 get-object --bucket <name> --key <key>`

### Secrets Manager

- `clm secrets list`
- `clm secrets get --name <secret-name>`
- `clm secrets put --name <secret-name> --value <value>`

Additional module commands are added as each module gains stateful support. The command
structure follows the pattern `clm <service> <action>` so new modules plug in naturally.
The CLI discovers available commands from the `/api/status` endpoint at runtime — no
hardcoded service list.

## Acceptance criteria

- [x] CLI lives in a separate repository `cloud-mock/cloudmock-cli`
- [x] Binary ships as `cloudmock` with `clm` as a built-in alias — both work
- [x] CLI can query running status and loaded modules
- [x] CLI supports the global commands listed above
- [x] CLI supports the SQS, S3, and Secrets Manager commands listed above
- [x] CLI discovers available commands from `/api/status` — new modules expose new commands
  automatically without any change to the CLI
- [x] CLI communicates with standalone mode over the REST API
- [x] CLI works without AWS SDK or AWS CLI installed
- [x] CLI returns clear error messages when CloudMock is not running or a service is not loaded

## Dependencies

- 0021 (standalone mode)
- 0024 (state store interface)
- 0032 (REST API)

## Notes

- The CLI is a thin HTTP client against the REST API. It has no direct dependency on
  CloudMock internals or WireMock.
- Consider whether this ships as a native binary using GraalVM native-image for faster startup
  or as a Java executable JAR. A native binary is the better developer experience — no JVM
  required to run the CLI.

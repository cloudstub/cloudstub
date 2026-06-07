# CLI tool for interacting with CloudMock

**Type:** dx

## Summary

Once standalone mode and the state store exist, developers need a way to inspect and manipulate mock state from the
terminal. The CLI lets users list resources, send test data, inspect state, and reset services without writing code
or installing the AWS CLI. The binary ships as `cloudmock` with `clm` as a built-in alias.

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

Additional module commands are added as each module gains stateful support. The command structure follows the
pattern `clm <service> <action>` so new modules plug in naturally.

## Acceptance criteria

- [ ] Binary ships as `cloudmock` with `clm` as a built-in alias — both work
- [ ] CLI can query running status and loaded modules
- [ ] CLI supports the global commands listed above
- [ ] CLI supports the SQS, S3, and Secrets Manager commands listed above
- [ ] CLI communicates with standalone mode over HTTP via an admin API
- [ ] CLI works without AWS SDK or AWS CLI installed
- [ ] CLI returns clear error messages when CloudMock is not running or a service is not loaded
- [ ] New module commands can be added without changing the CLI core

## Dependencies

- 0021 (standalone mode)
- 0024 (state store interface)

## Notes

- The CLI is a thin HTTP client against an admin API exposed by standalone mode. The admin API and the state store
  query interface from 0024 should be designed together.
- Each module registers its own CLI commands following the same SPI pattern used for stub registration. The CLI
  core discovers available commands at runtime based on which modules are loaded.
- Consider whether this ships as part of the main JAR (`java -jar cloudmock.jar sqs list-queues`) or as a separate
  native binary using GraalVM native-image for faster startup.

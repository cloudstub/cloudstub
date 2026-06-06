# CLI

`cloudmock-cli` is a small command-line client for a running [standalone](standalone.md) CloudMock
instance. It lets you inspect and drive mock state from the terminal — list resources, send test
data, reset services — without writing code or installing the AWS CLI.

The CLI is a **thin HTTP client over the [REST API](rest-api.md)**. It has no dependency on
CloudMock internals, WireMock, or any service module, and it does not need the AWS SDK. It
discovers the available commands at runtime from `GET /api/status`, so a CloudMock instance with
more modules loaded simply offers more commands — the CLI itself never changes.

## Build

```
./gradlew :cloudmock-cli:shadowJar
# output: cloudmock-cli/build/libs/cloudmock-cli.jar
```

Run it via the bundled launcher scripts in `cloudmock-cli/bin/` (`cloudmock` is the binary, `clm`
is an identical alias — both work), or directly with `java -jar`:

=== "Launcher script"

    ```
    ./cloudmock-cli/bin/clm status
    ```

=== "java -jar"

    ```
    java -jar cloudmock-cli/build/libs/cloudmock-cli.jar status
    ```

The examples below use `clm`.

## Connecting

The CLI talks only to the REST API port. Defaults match standalone mode, so with a server on the
default port no configuration is needed.

| Mechanism            | Host                  | API port                  |
|----------------------|-----------------------|---------------------------|
| Flag                 | `--host=example`      | `--api-port=9001`         |
| Environment variable | `CLOUDMOCK_HOST`      | `CLOUDMOCK_API_PORT`      |
| Default              | `localhost`           | `4567`                    |

```
clm --api-port=9001 status
CLOUDMOCK_API_PORT=9001 clm status
```

If the server is not reachable the CLI prints a clear message and exits non-zero:

```
$ clm status
CloudMock is not running at http://localhost:4567.
Start it with: java -jar cloudmock-standalone.jar
```

## Global commands

These are always available, regardless of which modules are loaded.

### `clm status`

Shows the running instance: ports, uptime, and each loaded module with its stub count.

```
$ clm status
mock port:     4566
api port:      4567
started at:    2026-06-06T10:00:00Z
uptime:        PT5M30S

Modules
-------
  s3               107 stub(s)
  secretsmanager   5 stub(s)
  sqs              8 stub(s)
```

### `clm reset`

Clears mock state. With no argument it resets everything (and clears the request history); with
`--service` it resets a single service.

```
clm reset
clm reset --service sqs
```

## Service commands

Service commands follow the pattern `clm <service> <action>`. They are **discovered at runtime**:
every module route advertised by `/api/status` becomes a subcommand, and each route parameter
becomes an option. Run `clm --help` to see the services the connected instance exposes, and
`clm <service> --help` for its actions.

The commands available today, with the reference modules loaded:

=== "SQS"

    ```
    clm sqs list-queues
    clm sqs send-message --queue orders --body "hello"
    clm sqs receive-message --queue orders
    clm sqs purge-queue --queue orders
    ```

=== "S3"

    ```
    clm s3 list-buckets
    clm s3 list-objects --bucket my-bucket
    clm s3 put-object --bucket my-bucket --key file.txt --body "contents"
    clm s3 get-object --bucket my-bucket --key file.txt
    ```

=== "Secrets Manager"

    ```
    clm secretsmanager list
    clm secretsmanager get --name my-secret
    clm secretsmanager put --name my-secret --value s3cr3t
    ```

Responses are printed as JSON:

```
$ clm sqs send-message --queue orders --body "hello"
{
  "messageId" : "f8eafb78-edcd-46f7-b78a-43b9e160cbef",
  "md5OfBody" : "5d41402abc4b2a76b9719d911017c592"
}
```

!!! note "Stateless responses"
    Like the rest of CloudMock, CLI responses are synthetic and stateless — `receive-message`
    does not return a previously sent message, and `list-objects` does not reflect a prior
    `put-object`. The CLI exercises the contract, not AWS semantics.

## How new modules appear automatically

Because commands come from `/api/status`, the CLI needs no change when a module is added:

- A module that implements [`CloudMockApiService`](module-authoring.md#8-exposing-cli-commands-via-the-rest-api)
  contributes routes under `/api/<service>/…`, each advertising a command name and parameters.
- Start CloudMock with that module on the classpath and its commands appear under `clm <service>`.
- Restrict the loaded modules with `--modules=<a,b>` and the CLI offers only those services — a
  service that is not loaded has no command at all.

## Error behaviour

- **Server not running** — a connection failure prints the "not running" message above and exits 1.
- **Service not loaded** — the service simply has no command; the CLI lists the commands that
  *are* available.
- **Missing required option** — picocli reports it and prints usage before any request is made.
- **Server-side error** — a non-2xx response is surfaced with the server's error message and a
  non-zero exit code, rather than being treated as success.

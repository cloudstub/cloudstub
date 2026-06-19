# CLI

The CloudStub CLI is a small command-line client for a running [standalone](standalone.md) CloudStub
instance. It lets you inspect and drive mock state from the terminal — list resources, send test
data, reset services — without writing code or installing the AWS CLI.

The CLI is a **thin HTTP client over the [REST API](rest-api.md)**. It discovers the available
commands at runtime from `GET /api/status`, so a CloudStub instance with more modules loaded simply
offers more commands — the CLI itself never changes.

It ships in the same `cloudstub-local` fat JAR as the server: the jar is **dual-mode**. With no
arguments (or an explicit `serve`) it starts the server; with a command token (`status`, `reset`,
`sqs send-message`, …) it runs the CLI against an already-running instance. The CLI path never boots
the mock, so commands start quickly.

## Install

Build the `cloudstub-local` fat JAR (requires Java 17+):

```
./gradlew :cloudstub-local:shadowJar
# output: cloudstub-local/build/libs/cloudstub-local.jar
```

Run it via the bundled launcher scripts in `cloudstub-local/bin/` (`cloudstub` is the binary, `clb`
is an identical short alias — both work; put `bin/` on your `PATH`), or directly with `java -jar`:

=== "Launcher script"

    ```
    ./bin/clb status
    ```

=== "java -jar"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar status
    ```

The examples below use `clb`.

## Connecting

The CLI talks only to the REST API port. Defaults match standalone mode, so with a server on the
default port no configuration is needed.

| Mechanism            | Host                  | API port                  |
|----------------------|-----------------------|---------------------------|
| Flag                 | `--host=example`      | `--api-port=9001`         |
| Environment variable | `CLOUDSTUB_HOST`      | `CLOUDSTUB_API_PORT`      |
| Default              | `localhost`           | `4567`                    |

```
clb --api-port=9001 status
CLOUDSTUB_API_PORT=9001 clb status
```

If the server is not reachable the CLI prints a clear message and exits non-zero:

```
$ clb status
CloudStub is not running at http://localhost:4567.
Start it with: cloudstub serve
```

## Global commands

These are always available, regardless of which modules are loaded.

### `clb status`

Shows the running instance: ports, uptime, and each loaded module with its stub count.

```
$ clb status
mock port:     4566
api port:      4567
started at:    2026-06-06T10:00:00Z
uptime:        PT5M30S

Modules
-------
  s3               107 stub(s)
  secretsmanager   5 stub(s)
  sqs              23 stub(s)
```

### `clb reset`

Clears mock state. With no argument it resets everything (and clears the request history); with
`--service` it resets a single service.

```
clb reset
clb reset --service sqs
```

## Service commands

Service commands follow the pattern `clb <service> <action>`. They are **discovered at runtime**:
every module route advertised by `/api/status` becomes a subcommand, and each route parameter
becomes an option. Run `clb --help` to see the services the connected instance exposes, and
`clb <service> --help` for its actions.

The commands available today, with the reference modules loaded:

=== "SQS"

    ```
    clb sqs list-queues
    clb sqs send-message --queue orders --body "hello"
    clb sqs receive-message --queue orders
    clb sqs purge-queue --queue orders
    ```

=== "S3"

    ```
    clb s3 list-buckets
    clb s3 list-objects --bucket my-bucket
    clb s3 put-object --bucket my-bucket --key file.txt --body "contents"
    clb s3 get-object --bucket my-bucket --key file.txt
    ```

=== "Secrets Manager"

    ```
    clb secretsmanager list
    clb secretsmanager get --name my-secret
    clb secretsmanager put --name my-secret --value s3cr3t
    ```

Responses are printed as JSON:

```
$ clb sqs send-message --queue orders --body "hello"
{
  "md5OfBody" : "5d41402abc4b2a76b9719d911017c592",
  "messageId" : "f8eafb78-edcd-46f7-b78a-43b9e160cbef"
}
```

!!! note "State-backed where the module supports it"
    The CLI and the AWS SDK share the **same state store**. For state-backed modules (SQS),
    `clb sqs receive-message` returns messages your app sent through the SDK, and a message sent with
    `clb sqs send-message` is visible to the SDK. State-backing rolls out per module — SQS is live; S3
    and Secrets Manager commands are still synthetic until their own state-backing lands.

## How new modules appear automatically

Because commands come from `/api/status`, the CLI needs no change when a module is added:

- A module that implements [`CloudStubApiService`](module-authoring.md#8-exposing-cli-commands-via-the-rest-api)
  contributes routes under `/api/<service>/…`, each advertising a command name and parameters.
- Start CloudStub with that module on the classpath and its commands appear under `clb <service>`.
- Restrict the loaded services with `--services=<a,b>` and the CLI offers only those services — a
  service that is not loaded has no command at all.

## Error behaviour

- **Server not running** — a connection failure prints the "not running" message above and exits 1.
- **Service not loaded** — the service simply has no command; the CLI lists the commands that
  *are* available.
- **Missing required option** — picocli reports it and prints usage before any request is made.
- **Server-side error** — a non-2xx response is surfaced with the server's error message and a
  non-zero exit code, rather than being treated as success.

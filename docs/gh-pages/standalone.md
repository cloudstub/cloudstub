# Standalone Mode

Standalone mode runs CloudStub as a long-lived process instead of inside a JUnit test. Start it once, leave it running,
and point your application at `http://localhost:4566`.

## When to use it

| Use case                                          | Recommended mode                                                  |
|---------------------------------------------------|-------------------------------------------------------------------|
| JUnit integration tests                           | [Embedded mode](getting-started.md) via `CloudStubExtension`      |
| `./gradlew bootRun` or other long-lived local dev | **Standalone mode**                                               |
| Docker Compose local environment                  | **Standalone mode**                                               |
| CI pipeline tests                                 | [Embedded mode](getting-started.md) — faster, no external process |

## Distribution model

The standalone server is a thin runtime: the launcher plus `cloudstub-core`. **No service modules are bundled.**
Service modules ship as separate jars that the launcher loads at runtime from a **plugin directory**. The model is:
download the server jar once, then download the module jars you want and drop them in the plugin directory.

- `--modules-dir` controls what is **available** — which module jars are on the classpath.
- `--services` (below) narrows what is **enabled** among those.

## Build the fat JAR

```
./gradlew :cloudstub-local:shadowJar
```

This produces `cloudstub-local/build/libs/cloudstub-local.jar` — the CloudStub engine (with shaded
WireMock/Jetty) and the launcher, and nothing else. Each service module (`cloudstub-sqs`, `cloudstub-sns`,
`cloudstub-secretsmanager`, `cloudstub-s3`) is a separate jar built by its own module.

## Add the modules you want

The launcher loads every `.jar` in the plugin directory (default `./modules`):

```
mkdir -p modules
cp path/to/cloudstub-sqs.jar path/to/cloudstub-s3.jar modules/
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs,s3
```

=== "Custom plugin directory (CLI flag)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --modules-dir=/opt/cloudstub/modules --services=sqs
    ```

=== "Custom plugin directory (environment variable)"

    ```
    CLOUDSTUB_MODULES_DIR=/opt/cloudstub/modules java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs
    ```

Plugin directory precedence: `--modules-dir` flag → `CLOUDSTUB_MODULES_DIR` env var → default `./modules`. An
explicitly provided `--modules-dir` that does not exist fails fast with a clear error; a missing or empty default
`./modules` is **not** fatal — the server starts and serves nothing.

## Start the server

=== "Default port (4566)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

=== "Custom port (CLI flag)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --port=9000
    ```

=== "Custom port (environment variable)"

    ```
    CLOUDSTUB_PORT=9000 java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

Port resolution precedence: `--port` flag → `CLOUDSTUB_PORT` env var → default `4566`.

## Select which services to enable

Services are **opt-in**: with no selection the server starts but loads nothing and prints a warning. Pass a
comma-separated list of service IDs to enable them. Services not listed are not registered and will not serve any
request.

=== "CLI flag"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs,secretsmanager
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_SERVICES=sqs,secretsmanager java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

Service selection precedence: `--services` flag → `CLOUDSTUB_SERVICES` env var → nothing enabled.

If you start with no `--services`, the server warns you and tells you how to enable services:

```
[CloudStub] WARNING: no services enabled — the mock will serve nothing.
[CloudStub]          Enable services with --services=<id>[,<id>...] or CLOUDSTUB_SERVICES=<id>[,<id>...].
[CloudStub]          Available services: sqs, sns, secretsmanager, s3
```

### Limit retained request history

The REST API records served requests for `GET /api/history`. In a long-lived process this journal is
capped at the last 1000 entries by default. Override the cap, or remove it entirely:

=== "CLI flag"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --max-history=5000
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_MAX_HISTORY=5000 java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

History cap precedence: `--max-history` flag → `CLOUDSTUB_MAX_HISTORY` env var → default `1000`.
Pass `unlimited` (or `none`) to retain every request.

If you name a service that is not on the classpath, the server fails fast with a clear error instead of starting up with a
silently missing service:

```
[CloudStub] Unknown service(s): dynamo. Available: sqs, sns, secretsmanager, s3
```

### Expected startup output

```
[CloudStub] Plugin directory: /path/to/modules
[CloudStub] Available services: sqs, sns, secretsmanager, s3
[CloudStub] Enabled services: sqs, secretsmanager
[CloudStub] State storage: persistent (.cloudstub)
[CloudStub] Request history: last 1000 entries
CloudStub started on port 4566
CloudStub API on port 4567
```

The **Plugin directory** line shows where module jars are loaded from; the **Available** line lists every service
discovered in that directory; the **Enabled** line lists the ones actually serving requests. If a stub is not being
served, check that its module jar is in the plugin directory (Available) and that its service appears on the Enabled
line.

The REST API is available at `http://localhost:4567` — see [REST API](rest-api.md) for the full reference, drive
the instance from the terminal with the [CLI](cli.md) (`clm` / `cloudstub`), or inspect it visually in the browser
with the [Console](console.md).

## Point your application at it

Set `AWS_ENDPOINT_URL` (AWS SDK v2 reads this automatically) before starting your application:

```
export AWS_ENDPOINT_URL=http://localhost:4566
./gradlew bootRun
```

Or configure it directly in your application's AWS client builder:

```java
SqsClient sqs = SqsClient.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();
```

## Verify a service end to end

The `cloudstub-example` app ships profile-gated demo runners for manual end-to-end checks against a running server. With the server started above (`--services=sqs`), run the SQS demo:

```
./gradlew :cloudstub-example:junit6:runExample -Pdemo=sqs
```

It publishes messages, peeks them with `ReceiveMessage`, then consumes them with `ReceiveMessage` + `DeleteMessage`, and logs the round-trip. See [Spring Boot Integration](spring-boot.md#run-the-app-against-a-standalone-server) for details.

## Stop the server

Press `Ctrl-C` or send `SIGTERM`. CloudStub prints a shutdown message and exits cleanly — no stack trace:

```
^C
[CloudStub] Shutting down...
```

## Logging

By default, CloudStub writes INFO-level output to stdout via `slf4j-simple`. To enable DEBUG output (stub
registration and full request/response bodies), pass `-Dcloudstub.debug=true` or set `CLOUDSTUB_DEBUG=true`. See
[Logging](logging.md) for the full reference, log levels, and how to plug in a custom implementation.

## Available service modules

Each module is a separate jar; drop the ones you want into the plugin directory:

| Module                     | Service         | Protocol            |
|----------------------------|-----------------|---------------------|
| `cloudstub-sqs`            | SQS             | JSON / X-Amz-Target |
| `cloudstub-sns`            | SNS             | XML / Form URL      |
| `cloudstub-secretsmanager` | Secrets Manager | JSON / X-Amz-Target |
| `cloudstub-s3`             | S3              | REST path           |

!!! note "Out-of-scope behaviours"
    CloudStub does not simulate IAM, DynamoDB conditional expressions, SQS FIFO ordering, or S3 multipart upload
    lifecycle. See the [architecture overview](index.md#how-it-works) for the full list.

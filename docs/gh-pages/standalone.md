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

## Build the fat JAR

```
./gradlew :cloudstub-standalone:shadowJar
```

This produces `cloudstub-standalone/build/libs/cloudstub-standalone.jar` — a single self-contained JAR with the
CloudStub engine and all current service modules bundled inside (SQS, SNS, Secrets Manager, S3).

## Start the server

=== "Default port (4566)"

    ```
    java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar
    ```

=== "Custom port (CLI flag)"

    ```
    java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar --port=9000
    ```

=== "Custom port (environment variable)"

    ```
    CLOUDSTUB_PORT=9000 java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar
    ```

Port resolution precedence: `--port` flag → `CLOUDSTUB_PORT` env var → default `4566`.

## Select which services to enable

Services are **opt-in**: with no selection the server starts but loads nothing and prints a warning. Pass a
comma-separated list of service IDs to enable them. Services not listed are not registered and will not serve any
request.

=== "CLI flag"

    ```
    java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar --services=sqs,secretsmanager
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_SERVICES=sqs,secretsmanager java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar
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
    java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar --max-history=5000
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_MAX_HISTORY=5000 java -jar cloudstub-standalone/build/libs/cloudstub-standalone.jar
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
[CloudStub] Available services: sqs, sns, secretsmanager, s3
[CloudStub] Enabled services: sqs, secretsmanager
[CloudStub] State storage: persistent (.cloudstub)
[CloudStub] Request history: last 1000 entries
CloudStub started on port 4566
CloudStub API on port 4567
```

The **Available** line lists every service bundled in the JAR; the **Enabled** line lists the ones actually serving
requests. If a stub is not being served, check that its service appears on the Enabled line.

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

## Bundled service modules

The standalone JAR bundles the following modules. No additional JARs are required:

| Module                     | Service         | Protocol            |
|----------------------------|-----------------|---------------------|
| `cloudstub-sqs`            | SQS             | JSON / X-Amz-Target |
| `cloudstub-sns`            | SNS             | XML / Form URL      |
| `cloudstub-secretsmanager` | Secrets Manager | JSON / X-Amz-Target |
| `cloudstub-s3`             | S3              | REST path           |

!!! warning "Responses are stateless"
    CloudStub returns templated responses derived from each request — it does **not** store state across calls. In
    standalone mode this means a message sent with `SendMessage` is **not** returned by a later `ReceiveMessage`; the
    receive call returns a synthetic placeholder message instead. Standalone mode is for exercising request/response
    wiring against a long-lived endpoint, not for stateful end-to-end flows. A stateful backend is tracked separately
    as a future design (state store interface).

!!! note "Out-of-scope behaviours"
    CloudStub does not simulate IAM, DynamoDB conditional expressions, SQS FIFO ordering, or S3 multipart upload
    lifecycle. See the [architecture overview](index.md#how-it-works) for the full list.

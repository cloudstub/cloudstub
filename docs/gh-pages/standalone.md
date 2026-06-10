# Standalone Mode

Standalone mode runs CloudMock as a long-lived process instead of inside a JUnit test. Start it once, leave it running,
and point your application at `http://localhost:4566`.

## When to use it

| Use case                                          | Recommended mode                                                  |
|---------------------------------------------------|-------------------------------------------------------------------|
| JUnit integration tests                           | [Embedded mode](getting-started.md) via `CloudMockExtension`      |
| `./gradlew bootRun` or other long-lived local dev | **Standalone mode**                                               |
| Docker Compose local environment                  | **Standalone mode**                                               |
| CI pipeline tests                                 | [Embedded mode](getting-started.md) — faster, no external process |

## Build the fat JAR

```
./gradlew :cloudmock-standalone:shadowJar
```

This produces `cloudmock-standalone/build/libs/cloudmock-standalone.jar` — a single self-contained JAR with the
CloudMock engine and all current service modules bundled inside (SQS, SNS, Secrets Manager, S3).

## Start the server

=== "Default port (4566)"

    ```
    java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

=== "Custom port (CLI flag)"

    ```
    java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar --port=9000
    ```

=== "Custom port (environment variable)"

    ```
    CLOUDMOCK_PORT=9000 java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

Port resolution precedence: `--port` flag → `CLOUDMOCK_PORT` env var → default `4566`.

## Select which modules to enable

By default every module bundled in the JAR is enabled. To run only a subset, pass a comma-separated list of service IDs.
Modules not listed are not registered and will not serve any request.

=== "CLI flag"

    ```
    java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar --modules=sqs,secretsmanager
    ```

=== "Environment variable"

    ```
    CLOUDMOCK_MODULES=sqs,secretsmanager java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

Module selection precedence: `--modules` flag → `CLOUDMOCK_MODULES` env var → all bundled modules.

### Limit retained request history

The REST API records served requests for `GET /api/history`. In a long-lived process this journal is
capped at the last 1000 entries by default. Override the cap, or remove it entirely:

=== "CLI flag"

    ```
    java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar --max-history=5000
    ```

=== "Environment variable"

    ```
    CLOUDMOCK_MAX_HISTORY=5000 java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

History cap precedence: `--max-history` flag → `CLOUDMOCK_MAX_HISTORY` env var → default `1000`.
Pass `unlimited` (or `none`) to retain every request.

If you name a module that is not on the classpath, the server fails fast with a clear error instead of starting up with a
silently missing service:

```
[CloudMock] Unknown module(s): dynamo. Available: sqs, sns, secretsmanager, s3
```

### Expected startup output

```
[CloudMock] Available modules: sqs, sns, secretsmanager, s3
[CloudMock] Enabled modules: sqs, secretsmanager
[CloudMock] State storage: persistent (.cloudmock)
[CloudMock] Request history: last 1000 entries
CloudMock started on port 4566
CloudMock API on port 4567
```

The **Available** line lists every module bundled in the JAR; the **Enabled** line lists the ones actually serving
requests. If a stub is not being served, check that its module appears on the Enabled line.

The REST API is available at `http://localhost:4567` — see [REST API](rest-api.md) for the full reference, drive
the instance from the terminal with the [CLI](cli.md) (`clm` / `cloudmock`), or inspect it visually in the browser
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

Press `Ctrl-C` or send `SIGTERM`. CloudMock prints a shutdown message and exits cleanly — no stack trace:

```
^C
[CloudMock] Shutting down...
```

## Bundled service modules

The standalone JAR bundles the following modules. No additional JARs are required:

| Module                     | Service         | Protocol            |
|----------------------------|-----------------|---------------------|
| `cloudmock-sqs`            | SQS             | JSON / X-Amz-Target |
| `cloudmock-sns`            | SNS             | XML / Form URL      |
| `cloudmock-secretsmanager` | Secrets Manager | JSON / X-Amz-Target |
| `cloudmock-s3`             | S3              | REST path           |

!!! warning "Responses are stateless"
    CloudMock returns templated responses derived from each request — it does **not** store state across calls. In
    standalone mode this means a message sent with `SendMessage` is **not** returned by a later `ReceiveMessage`; the
    receive call returns a synthetic placeholder message instead. Standalone mode is for exercising request/response
    wiring against a long-lived endpoint, not for stateful end-to-end flows. A stateful backend is tracked separately
    as a future design (state store interface).

!!! note "Out-of-scope behaviours"
    CloudMock does not simulate IAM, DynamoDB conditional expressions, SQS FIFO ordering, or S3 multipart upload
    lifecycle. See the [architecture overview](index.md#how-it-works) for the full list.

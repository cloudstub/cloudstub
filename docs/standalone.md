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

### Expected startup output

```
[CloudMock] Discovered modules: sqs, sns, secretsmanager, s3
CloudMock started on port 4566
```

The discovered modules line tells you exactly which service stubs are active. If a module is missing from that line, its
JAR is not on the classpath.

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

!!! note "Out-of-scope behaviours"
CloudMock does not simulate IAM, DynamoDB conditional expressions, SQS FIFO ordering, or S3 multipart upload
lifecycle. See the [architecture overview](index.md#how-it-works) for the full list.

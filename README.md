# CloudMock

> An ultra-lightweight, containerless AWS mock framework for the JVM.

## What is CloudMock?

CloudMock lets you test AWS service integrations without Docker, without credentials, and without waiting for a
container to spin up. It runs entirely in-process inside the JVM, starts in milliseconds, and loads only the service
modules your project actually needs.


## Why CloudMock

Testing AWS integrations on the JVM typically means running an external process — a Docker container, a Python runtime, or both. That adds startup time and environment dependencies to every test and CI run.

CloudMock runs inside the JVM itself. No container, no external process, no extra runtime.

## Installation

Add `cloudmock-core`, the JUnit 6 extension, and the service module(s) you need.

**Gradle**

```groovy
dependencies {
    testImplementation 'io.cloudmock:cloudmock-core:0.1.0'
    testImplementation 'io.cloudmock:cloudmock-junit6:0.1.0'

    // Service modules — add only what your project uses
    testImplementation 'io.cloudmock:cloudmock-sqs:0.1.0'
    testImplementation 'io.cloudmock:cloudmock-secretsmanager:0.1.0'

    // Matching AWS SDK v2 clients
    testImplementation 'software.amazon.awssdk:sqs:2.25.70'
    testImplementation 'software.amazon.awssdk:secretsmanager:2.25.70'
}
```

**Maven**

```xml

<dependencies>
    <dependency>
        <groupId>io.cloudmock</groupId>
        <artifactId>cloudmock-core</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.cloudmock</groupId>
        <artifactId>cloudmock-junit6</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.cloudmock</groupId>
        <artifactId>cloudmock-sqs</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Quickstart

```java
import io.cloudmock.core.CloudMock;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

CloudMock cloudMock = new CloudMock();
cloudMock.start();

SqsClient sqs = SqsClient.builder()
    .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
    .credentialsProvider(AnonymousCredentialsProvider.create())
    .region(Region.US_EAST_1)
    .build();

String queueUrl = sqs.createQueue(b -> b.queueName("my-queue")).queueUrl();

assertNotNull(queueUrl);

cloudMock.stop();
```

`CloudMock.start()` sets `aws.endpoint-url` automatically, so all AWS SDK v2 clients in the same JVM are redirected
with no further configuration. For full JUnit 6 lifecycle management, fault injection, and `@ExtendWith` usage, see the
[Getting Started guide](https://cloud-mock.github.io/cloudmockgetting-started/).

## Standalone mode

Besides running embedded in tests, CloudMock ships as a long-lived standalone server for local development — start it
once, leave it running, and point any application that reads `AWS_ENDPOINT_URL` at it. No Docker, no daemon.

```
./gradlew :cloudmock-standalone:shadowJar
java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
```

The server binds to port `4566` by default (override with `--port=<n>` or `CLOUDMOCK_PORT`). All bundled modules are
enabled unless you select a subset with `--modules=sqs,secretsmanager` (or `CLOUDMOCK_MODULES`). `Ctrl-C` shuts it down
cleanly.

```
export AWS_ENDPOINT_URL=http://localhost:4566
./gradlew bootRun
```

A companion CLI (`clm` / `cloudmock`) drives a running instance from the terminal — inspect status, send test data,
reset services — without the AWS CLI. It is a thin HTTP client that discovers its commands from the server at runtime.

```
./gradlew :cloudmock-cli:shadowJar
./cloudmock-cli/bin/clm status
./cloudmock-cli/bin/clm sqs send-message --queue orders --body "hello"
```

> Standalone mode serves the same stateless, templated responses as embedded mode — it does not persist state across
> calls. See the [Standalone Mode guide](https://cloud-mock.github.io/cloudmock/standalone/) and
> [CLI guide](https://cloud-mock.github.io/cloudmock/cli/) for full details.

## Supported services

| Module                     | Service         |
|----------------------------|-----------------|
| `cloudmock-sqs`            | Amazon SQS      |
| `cloudmock-secretsmanager` | Secrets Manager |
| `cloudmock-s3`             | Amazon S3       |
| `cloudmock-sns`            | Amazon SNS      |

**Tooling**

| Module              | Purpose                                                                        |
|---------------------|--------------------------------------------------------------------------------|
| `cloudmock-junit6`  | JUnit 6 extension — `@ExtendWith` + fault injection                            |
| `cloudmock-codegen` | Stub generator — produces a module skeleton from a Smithy model                |
| `cloudmock-sdk-v1`  | AWS SDK v1 companion — one-line endpoint redirection for teams still on SDK v1 |
| `cloudmock-cli`     | Command-line client (`clm` / `cloudmock`) for a running standalone instance    |

## Scope and limitations

CloudMock validates that your application calls AWS correctly and handles responses properly. It is not a reimplementation of AWS. Service-level behaviours like FIFO ordering, multipart upload lifecycle, conditional expressions, and IAM policy evaluation are out of scope. Tests that depend on these behaviours should run against a real AWS environment.

AWS SDK v2 is fully supported with automatic zero-config redirection. SDK v1 users can use cloudmock-sdk-v1 for a one-line per-client redirect.

## Contributing

If a module you need doesn't exist yet, you can build it. Each AWS service is an independent module implementing a
simple
two-method SPI (`CloudMockService` + `StubRegistrar`).

- **New module:** follow the [Module Authoring Guide](https://cloud-mock.github.io/cloudmock/module-authoring/)
- **New feature or bug:** open an issue in the [`issues/`](issues/) directory following the existing format
- **Stub generation:** use the [codegen tool](https://cloud-mock.github.io/cloudmock/codegen/) to generate a module
  skeleton from a Smithy model

Full documentation: **[CloudMock](https://cloud-mock.github.io/cloudmock/)**

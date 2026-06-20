# CloudStub

[![CI](https://github.com/cloudstub/cloudstub/actions/workflows/ci.yml/badge.svg)](https://github.com/cloudstub/cloudstub/actions/workflows/ci.yml)
[![CodeQL](https://github.com/cloudstub/cloudstub/actions/workflows/codeql.yml/badge.svg)](https://github.com/cloudstub/cloudstub/actions/workflows/codeql.yml)

> An ultra-lightweight, containerless AWS mock framework for the JVM.

## What is CloudStub?

CloudStub lets you test AWS service integrations without Docker, without credentials, and without waiting for a
container to spin up. It runs entirely in-process inside the JVM, starts in milliseconds, and loads only the service
modules your project actually needs.

## Why CloudStub

Testing AWS integrations on the JVM typically means running an external process: a Docker container, a Python runtime, or both. That adds startup time and environment dependencies to every test and CI run.

CloudStub runs inside the JVM itself. No container, no external process, no extra runtime.

## Installation

Add `cloudstub-testing` (it brings in `cloudstub-core` and the JUnit extension) and the service module(s) you need.

**Gradle**

```groovy
dependencies {
    testImplementation 'io.github.cloudstub:cloudstub-testing:0.1.0'

    // Service modules: add only what your project uses
    testImplementation 'io.github.cloudstub:cloudstub-sqs:0.1.0'
    testImplementation 'io.github.cloudstub:cloudstub-secretsmanager:0.1.0'

    // Matching AWS SDK v2 clients
    testImplementation 'software.amazon.awssdk:sqs:2.25.70'
    testImplementation 'software.amazon.awssdk:secretsmanager:2.25.70'
}
```

**Maven**

```xml

<dependencies>
    <dependency>
        <groupId>io.github.cloudstub</groupId>
        <artifactId>cloudstub-testing</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.github.cloudstub</groupId>
        <artifactId>cloudstub-sqs</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Quickstart

```java
import io.cloudstub.core.CloudStub;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

CloudStub cloudMock = new CloudStub();
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

`CloudStub.start()` sets `aws.endpoint-url` automatically, so all AWS SDK v2 clients in the same JVM are redirected
with no further configuration. For full JUnit lifecycle management, fault injection, and `@ExtendWith` usage, see the
[Getting Started guide](https://cloudstub.github.io/cloudstub/getting-started/).

## Standalone mode

Besides running embedded in tests, CloudStub ships as a long-lived standalone server for local development; start it
once, leave it running, and point any application that reads `AWS_ENDPOINT_URL` at it. No Docker, no daemon. Declare the
services you want and the launcher fetches each module jar from Maven Central on first use:

```
java -jar cloudstub-local.jar --services=sqs,secretsmanager
export AWS_ENDPOINT_URL=http://localhost:4566   # the default port
```

It shares the same core engine and state backend as embedded mode, so stateful modules return live data: a
`SendMessage` is returned by a later `ReceiveMessage`. Drive a running instance from the terminal with the companion
`cloudstub` / `clb` CLI (bundled in the same fat JAR), or open the **web console** at
[`http://localhost:4567/console`](http://localhost:4567/console) to inspect state and send test data from the browser:

```
clb status
clb sqs send-message --queue orders --body "hello"
```

See the [Standalone Mode guide](https://cloudstub.github.io/cloudstub/standalone/) and
[CLI guide](https://cloudstub.github.io/cloudstub/cli/) for ports, the plugin directory, auto-download, offline use, and
state persistence.

## Supported services

| Module                     | Service         |
| -------------------------- | --------------- |
| `cloudstub-sqs`            | Amazon SQS      |
| `cloudstub-secretsmanager` | Secrets Manager |
| `cloudstub-s3`             | Amazon S3       |
| `cloudstub-sns`            | Amazon SNS      |

**Tooling**

| Module              | Purpose                                                                            |
| ------------------- | ---------------------------------------------------------------------------------- |
| `cloudstub-testing` | Test aggregator: one dependency that brings `cloudstub-core` + the JUnit extension |
| `cloudstub-junit`   | JUnit extension (JUnit 5 and 6): `@ExtendWith` + fault injection                   |
| `cloudstub-codegen` | Stub generator: produces a module skeleton from a Smithy model                     |
| `cloudstub-sdk-v1`  | AWS SDK v1 companion: one-line endpoint redirection for teams still on SDK v1      |

The `cloudstub` / `clb` command-line client ships inside the `cloudstub-local` fat JAR (dual-mode: server or CLI).

## Scope and limitations

CloudStub validates that your application calls AWS correctly and handles responses properly. It is not a reimplementation of AWS. Service-level behaviours like FIFO ordering, multipart upload lifecycle, conditional expressions, and IAM policy evaluation are deferred for later, pending implementation as CloudStub matures (see the [Roadmap](ROADMAP.md)). Until then, tests that depend on these behaviours should run against a real AWS environment.

AWS SDK v2 is fully supported with automatic zero-config redirection. SDK v1 users can use cloudstub-sdk-v1 for a one-line per-client redirect.

## Contributing

If a module you need doesn't exist yet, you can build it. Each AWS service is an independent module implementing the
`CloudStubService` SPI.

- **New module:** follow the [Module Authoring Guide](https://cloudstub.github.io/cloudstub/module-authoring/); see
  the [Roadmap](ROADMAP.md) for the targeted services and their status
- **New feature or bug:** open an issue on the [GitHub issue tracker](https://github.com/cloudstub/cloudstub/issues)
- **Stub generation:** use the [codegen tool](https://cloudstub.github.io/cloudstub/codegen/) to generate a module
  skeleton from a Smithy model

Full documentation: **[CloudStub](https://cloudstub.github.io/cloudstub/)**

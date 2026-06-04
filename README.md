# CloudMock

> An ultra-lightweight, containerless AWS mock framework for the JVM.

## What is CloudMock?

CloudMock lets you test AWS service integrations without Docker, without credentials, and without waiting for a
container to spin up. It runs entirely in-process inside the JVM, starts in milliseconds, and loads only the service
modules your project actually needs.

---

## Why CloudMock

Local AWS testing today means running LocalStack — a full Docker container backed by a Python runtime. It works, but
it brings real costs: 5–30 seconds of startup on a modern machine (60+ seconds under CI resource constraints), a hard
Docker dependency that breaks lightweight runners, and every service module loaded into memory whether you use it or
not.

CloudMock runs inside the JVM. No container, no external process, no configuration.

|                   | CloudMock | LocalStack (free) | LocalStack (Pro) | Mockito / SDK mocks |
|-------------------|-----------|-------------------|------------------|---------------------|
| Startup time      | ~100ms    | 5–30s             | 5–30s            | Instant             |
| Docker required   | No        | Yes               | Yes              | No                  |
| Internet required | No        | No                | Yes (license)    | No                  |
| Tests HTTP layer  | Yes       | Yes               | Yes              | No                  |
| Modular footprint | Yes       | No                | No               | N/A                 |
| Open source       | Yes       | Partial           | No               | Yes                 |

---

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

---

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
[Getting Started guide](https://bnmosria.github.io/cloud-mock/getting-started/).

---

## Supported services

| Module                        | Service           |
|-------------------------------|-------------------|
| `cloudmock-sqs`               | Amazon SQS        |
| `cloudmock-secretsmanager`    | Secrets Manager   |

**Tooling**

| Module                | Purpose                                                  |
|-----------------------|----------------------------------------------------------|
| `cloudmock-junit6`    | JUnit 6 extension — `@ExtendWith` + fault injection      |
| `cloudmock-codegen`   | Stub generator — produces a module skeleton from a Smithy model |
| `cloudmock-sdk-v1`    | AWS SDK v1 companion — one-line endpoint redirection for teams still on SDK v1 |

---

## Scope and limitations

CloudMock simulates the AWS API surface well enough to test application logic, but it is not a full reimplementation of
AWS. The following are explicitly out of scope:

- AWS SDK v1 automatic zero-config redirection — `aws.endpoint-url` is SDK v2 only; SDK v1 users use `cloudmock-sdk-v1` for a one-line per-client redirect
- SQS FIFO deduplication and ordering semantics
- S3 multipart upload lifecycle and versioning
- DynamoDB conditional expressions and transaction semantics
- IAM policy evaluation

Tests that depend on these behaviours should use LocalStack or a real AWS environment.

---

## Contributing

CloudMock grows through community-contributed modules. Each AWS service is an independent module implementing a simple
two-method SPI (`CloudMockService` + `StubRegistrar`).

- **New module:** follow the [Module Authoring Guide](https://bnmosria.github.io/cloud-mock/module-authoring/)
- **New feature or bug:** open an issue in the [`issues/`](issues/) directory following the existing format
- **Stub generation:** use the [codegen tool](https://bnmosria.github.io/cloud-mock/codegen/) to generate a module skeleton from a Smithy model

Full documentation: **[bnmosria.github.io/cloud-mock](https://bnmosria.github.io/cloud-mock/)**

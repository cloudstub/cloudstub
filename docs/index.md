# CloudMock

> An ultra-lightweight, containerless AWS mock for JVM integration tests.

CloudMock runs entirely inside the JVM. No Docker. No external process. No credentials. Tests that touch AWS services
start at the same speed as tests that don't.

It also ships as a **standalone runnable JAR** for local development: start it once and any application that reads
`AWS_ENDPOINT_URL` connects to it — no container, no daemon, no setup.

---

## Why CloudMock

Testing AWS integrations on the JVM typically means running an external process — a Docker container, a Python runtime, or both. That adds startup time and environment dependencies to every test and CI run.

CloudMock answers a simpler question: what if the mock ran inside the JVM itself?

---

## How it works

The **core engine** boots an embedded HTTP server on a random port, sets the `aws.endpoint-url` system property to
redirect the AWS SDK v2, and discovers service modules via `ServiceLoader`. Each **service module** is an independently
installable JAR that registers its stubs through the `StubRegistrar` SPI. The underlying HTTP server (WireMock) is
completely hidden — you never interact with it directly.

First-party modules target the AWS SDK for Java **v2**. Teams still on **v1** can redirect their clients to CloudMock
with the `cloudmock-sdk-v1` companion — see [SDK v1 Support](sdk-v1.md).

---

## Quick example

```java

@ExtendWith(CloudMockExtension.class)
class OrderServiceTest {

    @Test
    void publishesOrderEvent() {
        SqsClient sqs = SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        String queueUrl = sqs.createQueue(b -> b.queueName("orders")).queueUrl();
        String messageId = sqs.sendMessage(b -> b
                .queueUrl(queueUrl)
                .messageBody("order-placed"))
            .messageId();

        assertNotNull(messageId);
    }
}
```

[Get started in five minutes →](getting-started.md)

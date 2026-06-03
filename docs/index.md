# CloudMock

> An ultra-lightweight, containerless AWS mock for JVM integration tests.

CloudMock runs entirely inside the JVM. No Docker. No external process. No credentials. Tests that touch AWS services start at the same speed as tests that don't.

---

## Why CloudMock

Testing against real AWS services or LocalStack means waiting for a container to start, keeping Docker available on every machine and CI runner, and loading every AWS service into memory whether you use it or not.

CloudMock answers a simpler question: what if the mock ran inside the JVM itself?

| | CloudMock | LocalStack (free) | Mockito / SDK mocks |
|---|---|---|---|
| Startup time | ~100 ms | 15–60 s | Instant |
| Docker required | No | Yes | No |
| Tests HTTP layer | Yes | Yes | No |
| Modular footprint | Yes | No | N/A |
| Open source | Yes | Partial | Yes |

---

## How it works

The **core engine** boots an embedded HTTP server on a random port, sets the `aws.endpoint-url` system property to redirect the AWS SDK v2, and discovers service modules via `ServiceLoader`. Each **service module** is an independently installable JAR that registers its stubs through the `StubRegistrar` SPI. The underlying HTTP server (WireMock) is completely hidden — you never interact with it directly.

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

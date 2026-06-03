# Getting Started

This guide walks you from zero to a passing CloudMock integration test in under five minutes.

## 1. Add dependencies

CloudMock is modular. Add `cloudmock-core`, the JUnit 6 extension, and only the AWS service modules your project needs.

=== "Gradle"

    ```groovy
    dependencies {
        testImplementation 'io.cloudmock:cloudmock-core:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-junit6:0.1.0'

        // Add one or more service modules
        testImplementation 'io.cloudmock:cloudmock-sqs:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-secretsmanager:0.1.0'

        // AWS SDK v2 clients for the services you use
        testImplementation 'software.amazon.awssdk:sqs:2.25.70'
        testImplementation 'software.amazon.awssdk:secretsmanager:2.25.70'
    }
    ```

=== "Maven"

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
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>sqs</artifactId>
            <version>2.25.70</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    ```

## 2. Write your first test

Annotate the test class with `@ExtendWith(CloudMockExtension.class)`. CloudMock starts before the first test, stops after the last, and sets `aws.endpoint-url` so the AWS SDK routes all traffic to the embedded server automatically.

```java
import io.cloudmock.junit6.CloudMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudMockExtension.class) // (1)!
class MyFirstCloudMockTest {

    @Test
    void sendMessageSucceeds() {
        SqsClient sqs = SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url"))) // (2)!
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        String queueUrl = sqs.createQueue(b -> b.queueName("my-queue")).queueUrl();
        String messageId = sqs.sendMessage(b -> b
                .queueUrl(queueUrl)
                .messageBody("hello"))
                .messageId();

        assertNotNull(messageId); // (3)!
    }
}
```

1. Starts CloudMock before the first test and stops it after the last. Service modules on the classpath are discovered automatically via `ServiceLoader` — no registration required.
2. CloudMock sets `aws.endpoint-url` to `http://localhost:<port>` before any test runs. The SDK reads it automatically.
3. CloudMock returns a well-formed response the SDK can parse. The assertion verifies end-to-end wiring, not AWS semantics.

## 3. Run it

```
./gradlew test
```

CloudMock starts in under 200 ms. No containers, no credentials, no network.

---

## Next steps

- [JUnit 6 Extension](junit6-extension.md) — learn the `@RegisterExtension` pattern for port access and explicit service registration
- [Spring Boot Integration](spring-boot.md) — use CloudMock with a full Spring Boot application context
- [Fault Injection](fault-injection.md) — simulate throttling, timeouts, and network brownouts

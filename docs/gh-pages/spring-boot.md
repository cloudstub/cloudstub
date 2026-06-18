# Spring Boot Integration

CloudStub works with Spring Boot integration tests through a JUnit 6 ordering guarantee: `@RegisterExtension static` extensions run their `beforeAll` callback before `SpringExtension` (registered via `@SpringBootTest`). This means CloudStub is up and `aws.endpoint-url` is set before Spring creates any AWS client beans.

The `cloudstub-example` module in this repository is a working Spring Boot application that demonstrates this pattern end-to-end.

## Application structure

Configure your AWS SDK clients as `@Bean` factories that read `aws.endpoint-url` from Spring's environment. System properties are included in Spring's `Environment`, so the property set by CloudStub is visible to `@Value`.

```java
// config/AwsConfig.java
@Configuration
public class AwsConfig {

    @Bean
    SqsClient sqsClient(@Value("${aws.endpoint-url:}") String endpointUrl) { // (1)!
        SqsClientBuilder builder = SqsClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.US_EAST_1);
        if (!endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }
}
```

1. The `:` default means the property is optional. In production `aws.endpoint-url` is absent and the SDK uses real AWS endpoints. In tests CloudStub sets it before the context starts.

Your services are plain Spring `@Service` classes with no CloudStub imports — see [`QueuePublisher`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/java/io/cloudstub/example/service/QueuePublisher.java) and [`SecretLoader`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/java/io/cloudstub/example/service/SecretLoader.java) in `cloudstub-example` for the full code.

## Integration tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext // (1)!
class QueuePublisherIntegrationTest {

    @RegisterExtension
    static CloudStubExtension cloudMock = new CloudStubExtension(); // (2)!

    @Autowired QueuePublisher publisher;

    @Test
    void publishCreatesQueueAndReturnsMessageId() {
        String messageId = publisher.publish("order-placed");
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }
}
```

1. Forces Spring to discard the context after this test class. Without it a cached context from a previous class (pointing at a different CloudStub port) would be reused.
2. `@RegisterExtension static` runs before `SpringExtension`. By the time Spring boots the application context, `aws.endpoint-url` is already set and the client beans pick it up.

See the full working tests in `cloudstub-example`:

- [`QueuePublisherIntegrationTest`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/test/java/io/cloudstub/example/QueuePublisherIntegrationTest.java)
- [`SecretLoaderIntegrationTest`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/test/java/io/cloudstub/example/SecretLoaderIntegrationTest.java)

## Pointing an application at CloudStub

In **embedded mode** (the integration tests above) no endpoint configuration is needed: `CloudStubExtension` sets the `aws.endpoint-url` system property to its embedded port before Spring builds any client bean, so the `@Value("${aws.endpoint-url:}")` injection picks it up automatically. In **standalone mode** CloudStub runs as a separate process, so the application must be told where it is. Any of these supply the endpoint:

- **`AWS_ENDPOINT_URL` environment variable** — AWS SDK v2 reads it directly, so it works even without the `@Value` wiring. `export AWS_ENDPOINT_URL=http://localhost:4566` before `bootRun`.
- **`aws.endpoint-url` property** — on the command line (`--aws.endpoint-url=...`), as a system property (`-Daws.endpoint-url=...`), or from an `application.properties` file. Read by the example [`AwsConfig`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/java/io/cloudstub/example/config/AwsConfig.java).
- **Manual `endpointOverride(...)`** — call it directly on the client builder for explicit per-client control.

### Environment profiles

The example app carries the endpoint on a Spring profile:

- **`local`** — [`application-local.properties`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/resources/application-local.properties) sets `aws.endpoint-url=http://localhost:4566`, so the clients hit a standalone CloudStub server.
- **`prod`** — [`application-prod.properties`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/resources/application-prod.properties) sets no endpoint override, so the SDK uses the real regional endpoints.

The base [`application.properties`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/junit6/src/main/resources/application.properties) sets no endpoint, so with no profile active the SDK uses real AWS. Run against a standalone server with `--spring.profiles.active=local`.

### Resolution precedence

When client beans read `aws.endpoint-url` through Spring, Spring's property precedence decides which value wins. From highest to lowest:

1. **Command-line argument** — `--aws.endpoint-url=http://...`.
2. **System property** — `-Daws.endpoint-url=http://...` (the form `CloudStubExtension` sets in tests).
3. **Active profile properties** — `application-local.properties` (`aws.endpoint-url=http://localhost:4566`) when `local` is active.
4. **Base `application.properties`** — no endpoint, so the SDK uses real AWS.

So a test's injected system property outranks the profile, and an explicit `--aws.endpoint-url` outranks both.

### From the IDE

Running the application from an IDE against a standalone server needs two things: a [standalone CloudStub server](standalone.md) running on the endpoint, and the `local` profile active. Set **Active profiles: `local`** in the run configuration (or add `-Dspring.profiles.active=local` to the VM options); `application-local.properties` then points the clients at `http://localhost:4566`.

## Dependencies

The `cloudstub-core` artifact shades its internal WireMock and Jetty dependencies, so it does not interfere with Spring Boot's own dependency management. You can use the Spring Boot BOM as normal.

=== "Gradle"

    ```groovy
    dependencies {
        implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.6')
        implementation 'org.springframework.boot:spring-boot-starter'
        implementation 'software.amazon.awssdk:sqs:2.25.70'
        implementation 'software.amazon.awssdk:secretsmanager:2.25.70'

        testImplementation 'io.github.cloudstub:cloudstub-testing:0.1.0'
        testImplementation 'io.github.cloudstub:cloudstub-sqs:0.1.0'
        testImplementation 'io.github.cloudstub:cloudstub-secretsmanager:0.1.0'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }
    ```

=== "Maven"

    ```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>4.0.6</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>sqs</artifactId>
            <version>2.25.70</version>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>secretsmanager</artifactId>
            <version>2.25.70</version>
        </dependency>

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
        <dependency>
            <groupId>io.github.cloudstub</groupId>
            <artifactId>cloudstub-secretsmanager</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    ```

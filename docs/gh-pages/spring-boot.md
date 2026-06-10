# Spring Boot Integration

CloudMock works with Spring Boot integration tests through a JUnit 6 ordering guarantee: `@RegisterExtension static` extensions run their `beforeAll` callback before `SpringExtension` (registered via `@SpringBootTest`). This means CloudMock is up and `aws.endpoint-url` is set before Spring creates any AWS client beans.

The `cloudmock-example` module in this repository is a working Spring Boot application that demonstrates this pattern end-to-end.

## Application structure

Configure your AWS SDK clients as `@Bean` factories that read `aws.endpoint-url` from Spring's environment. System properties are included in Spring's `Environment`, so the property set by CloudMock is visible to `@Value`.

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

1. The `:` default means the property is optional. In production `aws.endpoint-url` is absent and the SDK uses real AWS endpoints. In tests CloudMock sets it before the context starts.

Your services are plain Spring `@Service` classes with no CloudMock imports — see [`EventPublisher`](https://github.com/cloud-mock/cloudmock/blob/main/cloudmock-example/src/main/java/io/cloudmock/example/service/EventPublisher.java) and [`SecretLoader`](https://github.com/cloud-mock/cloudmock/blob/main/cloudmock-example/src/main/java/io/cloudmock/example/service/SecretLoader.java) in `cloudmock-example` for the full code.

## Integration tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext // (1)!
class EventPublisherIntegrationTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension(); // (2)!

    @Autowired EventPublisher publisher;

    @Test
    void publishCreatesQueueAndReturnsMessageId() {
        String messageId = publisher.publish("order-placed");
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }
}
```

1. Forces Spring to discard the context after this test class. Without it a cached context from a previous class (pointing at a different CloudMock port) would be reused.
2. `@RegisterExtension static` runs before `SpringExtension`. By the time Spring boots the application context, `aws.endpoint-url` is already set and the client beans pick it up.

See the full working tests in `cloudmock-example`:

- [`EventPublisherIntegrationTest`](https://github.com/cloud-mock/cloudmock/blob/main/cloudmock-example/src/test/java/io/cloudmock/example/EventPublisherIntegrationTest.java)
- [`SecretLoaderIntegrationTest`](https://github.com/cloud-mock/cloudmock/blob/main/cloudmock-example/src/test/java/io/cloudmock/example/SecretLoaderIntegrationTest.java)

## Dependencies

The `cloudmock-core` artifact shades its internal WireMock and Jetty dependencies, so it does not interfere with Spring Boot's own dependency management. You can use the Spring Boot BOM as normal.

=== "Gradle"

    ```groovy
    dependencies {
        implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.6')
        implementation 'org.springframework.boot:spring-boot-starter'
        implementation 'software.amazon.awssdk:sqs:2.25.70'
        implementation 'software.amazon.awssdk:secretsmanager:2.25.70'

        testImplementation 'io.cloudmock:cloudmock-core:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-junit6:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-sqs:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-secretsmanager:0.1.0'
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
    ```

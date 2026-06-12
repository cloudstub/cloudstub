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

Your services are plain Spring `@Service` classes with no CloudStub imports — see [`EventPublisher`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/src/main/java/io/cloudstub/example/service/EventPublisher.java) and [`SecretLoader`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/src/main/java/io/cloudstub/example/service/SecretLoader.java) in `cloudstub-example` for the full code.

## Integration tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext // (1)!
class EventPublisherIntegrationTest {

    @RegisterExtension
    static CloudStubExtension cloudMock = new CloudStubExtension(); // (2)!

    @Autowired EventPublisher publisher;

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

- [`EventPublisherIntegrationTest`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/src/test/java/io/cloudstub/example/EventPublisherIntegrationTest.java)
- [`SecretLoaderIntegrationTest`](https://github.com/cloudstub/cloudstub/blob/main/cloudstub-example/src/test/java/io/cloudstub/example/SecretLoaderIntegrationTest.java)

## Dependencies

The `cloudstub-core` artifact shades its internal WireMock and Jetty dependencies, so it does not interfere with Spring Boot's own dependency management. You can use the Spring Boot BOM as normal.

=== "Gradle"

    ```groovy
    dependencies {
        implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.6')
        implementation 'org.springframework.boot:spring-boot-starter'
        implementation 'software.amazon.awssdk:sqs:2.25.70'
        implementation 'software.amazon.awssdk:secretsmanager:2.25.70'

        testImplementation 'io.cloudstub:cloudstub-core:0.1.0'
        testImplementation 'io.cloudstub:cloudstub-junit:0.1.0'
        testImplementation 'io.cloudstub:cloudstub-sqs:0.1.0'
        testImplementation 'io.cloudstub:cloudstub-secretsmanager:0.1.0'
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
            <groupId>io.cloudstub</groupId>
            <artifactId>cloudstub-core</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cloudstub</groupId>
            <artifactId>cloudstub-junit</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cloudstub</groupId>
            <artifactId>cloudstub-sqs</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cloudstub</groupId>
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

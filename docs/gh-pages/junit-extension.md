# JUnit Extension

`CloudStubExtension` manages the CloudStub lifecycle around a test class and applies fault injection annotations to individual test methods.

Compatible with JUnit 5 and JUnit 6. JUnit 4 is not supported.

## Dependency

Most projects get `cloudstub-junit` transitively via [`cloudstub-testing`](getting-started.md); add it directly only to use the extension without the aggregator. `cloudstub-junit` declares JUnit as `compileOnly`, so you bring your own JUnit version; the module does not force one on your classpath.

=== "Gradle"

    ```groovy
    testImplementation 'io.github.cloudstub:cloudstub-junit:0.1.0'
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.cloudstub</groupId>
        <artifactId>cloudstub-junit</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
    ```

## Usage patterns

### `@ExtendWith`: zero boilerplate

The simplest form. Service modules on the classpath are discovered via `ServiceLoader` automatically.

```java
@ExtendWith(CloudStubExtension.class)
class OrderServiceTest {

    @Test
    void placingAnOrderPublishesIt() {
        // aws.endpoint-url is already set, so build clients and call AWS normally.
        SqsClient sqs = SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();
        String queueUrl = sqs.createQueue(b -> b.queueName("orders")).queueUrl();

        new OrderService(sqs, queueUrl).placeOrder("sku-42");

        String body = sqs.receiveMessage(b -> b.queueUrl(queueUrl)).messages().get(0).body();
        assertTrue(body.contains("sku-42"));
    }
}
```

### `@RegisterExtension`: port access and explicit registration

Use this form when you need the server port directly (e.g. to build clients once in `@BeforeAll`) or to register service modules explicitly rather than relying on `ServiceLoader`.

```java
class OrderServiceTest {

    @RegisterExtension
    static CloudStubExtension cloudMock = new CloudStubExtension()
            .withService(new CloudStubSqsService());         // (1)!

    static SqsClient sqsClient;

    @BeforeAll
    static void setUp() {
        sqsClient = SqsClient.builder()
            .endpointOverride(URI.create("http://localhost:" + cloudMock.port())) // (2)!
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();
    }

    @Test
    void placingAnOrderPublishesIt() {
        String queueUrl = sqsClient.createQueue(b -> b.queueName("orders")).queueUrl();

        new OrderService(sqsClient, queueUrl).placeOrder("sku-42");

        String body = sqsClient.receiveMessage(b -> b.queueUrl(queueUrl)).messages().get(0).body();
        assertTrue(body.contains("sku-42"));
    }
}
```

1. `.withService()` adds a module explicitly, in addition to any modules discovered via `ServiceLoader`. Useful in module-level tests where the classpath structure may prevent auto-discovery.
2. `cloudMock.port()` is only valid after `beforeAll` has run, so it is safe inside `@BeforeAll` and all `@Test` methods.

## Lifecycle

`CloudStubExtension` creates one `CloudStub` instance per test class. The lifecycle is:

```
beforeAll  → CloudStub.start()   (server up, aws.endpoint-url set)
beforeEach → fault annotations applied
test runs
afterEach  → all faults cleared
afterAll   → CloudStub.stop()    (server down, aws.endpoint-url cleared)
```

Each test class gets its own independent instance. One class stopping never affects another class's running instance.

## Fault injection

Fault annotations can be placed on individual test methods. Faults are always cleared after each test, even if the test throws.

```java
@ExtendWith(CloudStubExtension.class)
class ResilienceTest {

    @SimulateThrottle(service = "sqs")
    @Test
    void handlesThrottling() {
        // SqsException with errorCode "ThrottlingException"
    }

    @Test
    void normalAfterThrottle() {
        // Fault was cleared, so a normal response is returned.
    }
}
```

See [Fault Injection](fault-injection.md) for all three annotations and their behaviour.

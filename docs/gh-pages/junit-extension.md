# JUnit Extension

`CloudMockExtension` manages the CloudMock lifecycle around a test class and applies fault injection annotations to individual test methods.

Compatible with JUnit 5 and JUnit 6. JUnit 4 is not supported.

## Dependency

`cloudmock-junit` declares JUnit as `compileOnly` — bring your own JUnit version; the module does not force one on your classpath.

=== "Gradle"

    ```groovy
    testImplementation 'io.cloudmock:cloudmock-junit:0.1.0'
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.cloudmock</groupId>
        <artifactId>cloudmock-junit</artifactId>
        <version>0.1.0</version>
        <scope>test</scope>
    </dependency>
    ```

## Usage patterns

### `@ExtendWith` — zero boilerplate

The simplest form. Service modules on the classpath are discovered via `ServiceLoader` automatically.

```java
@ExtendWith(CloudMockExtension.class)
class MyTest {

    @Test
    void test() {
        // aws.endpoint-url is already set — build clients and call AWS normally
        SqsClient sqs = SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        assertNotNull(sqs.createQueue(b -> b.queueName("q")).queueUrl());
    }
}
```

### `@RegisterExtension` — port access and explicit registration

Use this form when you need the server port directly (e.g. to build clients once in `@BeforeAll`) or to register service modules explicitly rather than relying on `ServiceLoader`.

```java
class MyTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension()
            .withService(new CloudMockSqsService());         // (1)!

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
    void test() {
        assertNotNull(sqsClient.createQueue(b -> b.queueName("q")).queueUrl());
    }
}
```

1. `.withService()` adds a module explicitly, in addition to any modules discovered via `ServiceLoader`. Useful in module-level tests where the classpath structure may prevent auto-discovery.
2. `cloudMock.port()` is only valid after `beforeAll` has run — safe inside `@BeforeAll` and all `@Test` methods.

## Lifecycle

`CloudMockExtension` creates one `CloudMock` instance per test class. The lifecycle is:

```
beforeAll  → CloudMock.start()   (server up, aws.endpoint-url set)
beforeEach → fault annotations applied
test runs
afterEach  → all faults cleared
afterAll   → CloudMock.stop()    (server down, aws.endpoint-url cleared)
```

Each test class gets its own independent instance. One class stopping never affects another class's running instance.

## Fault injection

Fault annotations can be placed on individual test methods. Faults are always cleared after each test, even if the test throws.

```java
@ExtendWith(CloudMockExtension.class)
class ResilienceTest {

    @SimulateThrottle(service = "sqs")
    @Test
    void handlesThrottling() {
        // SqsException with errorCode "ThrottlingException"
    }

    @Test
    void normalAfterThrottle() {
        // Fault was cleared — normal response returned
    }
}
```

See [Fault Injection](fault-injection.md) for all three annotations and their behaviour.

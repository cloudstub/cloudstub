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

## Auto-downloading service modules

`withModule(String)` and `withModules(...)` name published modules by service id. The extension downloads each named module jar (`io.github.cloudstub:cloudstub-<serviceId>`) from Maven Central in `beforeAll` and registers it, so a module without an SDK-side component needs no per-module dependency: only `cloudstub-testing` (or `cloudstub-junit`) is required.

```java
@RegisterExtension
static CloudStubExtension cloudMock = new CloudStubExtension()
        .withModules("sqs", "secretsmanager");   // (1)!
```

1. Use `withModule("sqs")` for a single service. Repeated ids (across `withModule` and `withModules`) collapse to one download.

Downloads are cached under `~/.cloudstub/modules` by default, so a module fetched once is reused across test classes, runs, and JVMs. A cached module is never re-downloaded. The first run needs network access to Maven Central; later runs are offline-safe.

A module that is already a test dependency is discovered automatically via `ServiceLoader` at startup, so it does not need to be listed in `withModules(...)`. If it is listed anyway, the classpath copy is used and it is not downloaded or registered twice.

`withService` and `withModule` are distinct operations: `withService(CloudStubService)` (and its plural `withServices(...)`) registers an in-process instance, for custom or test-local stubs that are not published artifacts; `withModule`/`withModules` downloads a published module by id.

Optional settings:

| Method                       | Default                  | Purpose                                          |
| ---------------------------- | ------------------------ | ------------------------------------------------ |
| `withModuleVersion(String)`  | running core version     | Pin the module version (recommended for determinism). |
| `withModulesCacheDir(Path)`  | `~/.cloudstub/modules`   | Directory used to cache downloaded module jars.  |
| `withMavenBaseUrl(String)`   | Maven Central            | Repository base URL (e.g. a corporate mirror).   |

When the exact requested version is not published, the highest published version at most the requested one is resolved and fetched. Each download is checksum-verified before it is registered.

### What `withModules` can provision

`withModules` provisions a module's **server-side stubs only**.

- **Download-only modules** have no client-side component. They are fully provisioned by `withModules` and need no per-module dependency. This is the default; most modules fall here.
- **Modules with a client-side AWS SDK component** cannot be fully provisioned by download. The AWS SDK discovers execution interceptors (`software/amazon/awssdk/global/handlers/execution.interceptors`) from its own application classloader, not from the isolated classloader a downloaded jar is loaded into, so a downloaded jar's interceptor is never registered with the SDK.

A module in the second group is not provisioned with `withModules`. Instead, use one of:

| Route | How it works |
| ----- | ------------ |
| Declare the module as a test dependency | Its interceptor is on the application classpath where the SDK finds it, and the stubs are discovered automatically via `ServiceLoader`. Do not also list it in `withModules(...)`. |
| Configure the AWS client to not need the interceptor | Removes the dependency on the client-side component, so the downloaded stubs suffice and `withModules(...)` works. |

**S3 is the only such module today.** `cloudstub-s3` ships `S3VirtualHostStyleInterceptor`, which rewrites virtual-hosted-style requests (`mybucket.localhost`) to the path-style addressing CloudStub's stubs serve. Without it on the application classpath a default `S3Client` sends virtual-hosted-style requests that no stub matches, and S3 calls fail with `404`. So either declare `cloudstub-s3` as a test dependency, or enable path-style access on the `S3Client` (`pathStyleAccessEnabled` / `forcePathStyle(true)`) and provision it with `withModules`.

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

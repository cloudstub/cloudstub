# SDK v1 Support

CloudMock's first-party service modules target the **AWS SDK for Java v2**. For teams still mid-migration, the
`cloudmock-sdk-v1` companion library lets AWS SDK **v1** clients reach a running CloudMock instance.

!!! warning "What the companion does — and does not — do"
    `cloudmock-sdk-v1` redirects the **endpoint** of an SDK v1 client to CloudMock. That is all. It guarantees
    **connectivity**; it does not provide v1-shaped stub responses. See
    [Connectivity vs response fidelity](#connectivity-vs-response-fidelity) below before you rely on it.

The AWS Java SDK v1 reached [end-of-support on 2025-12-31](https://aws.amazon.com/de/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/).
The companion exists to support teams mid-migration. **Per-service first-party v1 stubs are explicitly a non-goal** —
new service modules target the v2 protocol shape only.

## Dependencies

Add the companion, a CloudMock service module, and the `com.amazonaws:aws-java-sdk-*` client for the service you call.

=== "Gradle"

    ```groovy
    dependencies {
        testImplementation 'io.cloudmock:cloudmock-core:0.1.0'
        testImplementation 'io.cloudmock:cloudmock-sdk-v1:0.1.0'

        // A CloudMock service module (see the fidelity note below)
        testImplementation 'io.cloudmock:cloudmock-sns:0.1.0'

        // The AWS SDK v1 client you are migrating away from
        testImplementation 'com.amazonaws:aws-java-sdk-sns:1.12.770'
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
            <artifactId>cloudmock-sdk-v1</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cloudmock</groupId>
            <artifactId>cloudmock-sns</artifactId>
            <version>0.1.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-sns</artifactId>
            <version>1.12.770</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    ```

## Redirecting a v1 client

`CloudMockV1Endpoints.forPort(cloudMock.port())` returns an `EndpointConfiguration` you pass straight to any v1 client
builder's `.withEndpointConfiguration(...)`:

```java
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import io.cloudmock.core.CloudMock;
import io.cloudmock.sdkv1.CloudMockV1Endpoints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class MyV1Test {

    static CloudMock cloudMock;
    static AmazonSNS snsClient;

    @BeforeAll
    static void start() {
        cloudMock = new CloudMock();
        cloudMock.start();

        snsClient = AmazonSNSClientBuilder.standard()
                .withEndpointConfiguration(CloudMockV1Endpoints.forPort(cloudMock.port())) // (1)!
                .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())) // (2)!
                .build();
    }

    @AfterAll
    static void stop() {
        if (snsClient != null) snsClient.shutdown();
        if (cloudMock != null) cloudMock.stop();
    }
}
```

1. Points the client at `http://localhost:<port>` with a dummy signing region of `us-east-1`.
2. SDK v1 requires credentials to build a client. Anonymous credentials satisfy that requirement.

!!! note "The signing region and credentials are inert"
    SDK v1 requires a signing region and credentials to construct a client, so `forPort` supplies a dummy region of
    `us-east-1` and the example uses `AnonymousAWSCredentials`. CloudMock does not verify signatures, so **neither value
    affects stub matching** — they exist only to satisfy the v1 builder.

The companion is JUnit-agnostic — `@BeforeAll`/`@AfterAll` above are illustrative; you can drive CloudMock from any
lifecycle.

## Connectivity vs response fidelity

This is the boundary that matters. The companion redirects the **endpoint**; it does not translate protocols.

- Most first-party modules — **SQS**, Secrets Manager, DynamoDB — target the **SDK v2** protocol shape: JSON with an
  `X-Amz-Target` header. SDK v1 speaks the **XML / QUERY** protocol, identifying the operation with an `Action`
  form-body parameter, so a v1 call to these services matches no first-party stub.
- **SNS is the exception:** it uses the XML / QUERY protocol in *both* v1 and v2, so the first-party `cloudmock-sns`
  module's `Action`-keyed stubs can already match a v1 SNS call — provided `cloudmock-sns` is on your classpath.

For the JSON/`X-Amz-Target` services, a v1 call therefore arrives in a shape that no first-party stub matches, so
**WireMock returns 404** and the v1 client raises an `AmazonServiceException`. Connectivity is proven (you reached the
server); response fidelity is not (no stub answered). The companion's own
[`CloudMockV1EndpointsTest`](https://github.com/cloud-mock/cloud-mock/blob/main/cloudmock-sdk-v1/src/test/java/io/cloudmock/sdkv1/CloudMockV1EndpointsTest.java)
asserts exactly this: a v1 `listQueues()` reaches CloudMock and gets an HTTP error response back, proving the connection
without claiming a matched stub.

| Concern | Guaranteed? | How |
|---|---|---|
| Connectivity (v1 client reaches CloudMock) | ✅ Yes | `CloudMockV1Endpoints.forPort(...)` |
| Response fidelity (a stub matches and answers) | ⚠️ Your responsibility | Author an XML/QUERY stub — see below |

## Bring your own stub

To get a *populated* response for a v1 call against a JSON/`X-Amz-Target` service, register a stub in the v1
(XML / QUERY) protocol yourself. You author a `CloudMockService` and register through
`registerXmlFormStub(actionName, responseTemplate)` — keyed on the `Action` form parameter the v1 client sends. The
full SPI walkthrough lives in the [Module Authoring guide](module-authoring.md).

The short version below uses SNS `Publish`. SNS shares the XML / QUERY protocol across v1 and v2, so the first-party
`cloudmock-sns` module would also serve this call; the example deliberately omits `cloudmock-sns` from its classpath to
demonstrate the bring-your-own-stub path in isolation — the general escape hatch for any v1 operation no first-party
module covers:

```java
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

public final class SnsV1PublishStubService implements CloudMockService {

    private static final String PUBLISH_RESPONSE = """
            <PublishResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
              <PublishResult>
                <MessageId>{{randomValue type='UUID'}}</MessageId>
              </PublishResult>
              <ResponseMetadata><RequestId>{{randomValue type='UUID'}}</RequestId></ResponseMetadata>
            </PublishResponse>""";

    @Override
    public String serviceId() {
        return "sns";
    }

    @Override
    public void register(StubRegistrar registrar) {
        registrar.registerXmlFormStub("Publish", PUBLISH_RESPONSE); // (1)!
    }
}
```

1. Matches `Action=Publish` in the form body — the wire shape a v1 SNS client produces — and returns the XML response
   the v1 client knows how to parse.

Install your stub explicitly with `withService(...)` and drive it with a real v1 client. A populated `MessageId` proves
the stub matched and the response was parsed — fidelity, not just connectivity:

```java
@RegisterExtension
static CloudMockExtension cloudMock =
        new CloudMockExtension().withService(new SnsV1PublishStubService()); // (1)!

// ... build the v1 client against CloudMockV1Endpoints.forPort(cloudMock.port()) ...

PublishResult result = snsClient.publish(
        "arn:aws:sns:us-east-1:000000000000:demo-topic", "hello from SDK v1");

assertNotNull(result.getMessageId()); // populated — the XML/QUERY stub was served
```

1. `withService(...)` installs the user-authored stub. The `cloudmock-sns` first-party module is not on the test
   classpath, so no other `Publish` stub competes with it. Plain `new CloudMock().withService(...)` works too if
   you are not using the JUnit 6 extension.

Both files above are real, compiling code, exercised on every `./gradlew build`:

- [`SnsV1PublishStubService`](https://github.com/cloud-mock/cloud-mock/blob/main/cloudmock-example/src/test/java/io/cloudmock/example/sdkv1/SnsV1PublishStubService.java)
- [`SnsV1PublishStubExampleTest`](https://github.com/cloud-mock/cloud-mock/blob/main/cloudmock-example/src/test/java/io/cloudmock/example/sdkv1/SnsV1PublishStubExampleTest.java)

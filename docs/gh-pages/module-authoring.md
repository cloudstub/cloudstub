# Module Authoring Guide

This guide walks through building a CloudMock service module from scratch. At the end you will have a working module
that integrates with the core engine via `ServiceLoader`, registers its stubs through `StubRegistrar`, and is tested
with real AWS SDK v2 clients.

Use `cloudmock-sqs` and `cloudmock-secretsmanager` as reference implementations throughout.

---

## 1. Choose the right protocol

Every AWS service uses one of three wire protocols. Your module implements whichever one its service uses.

| Protocol            | Services                       | Matched on                    | `StubRegistrar` method   |
|---------------------|--------------------------------|-------------------------------|--------------------------|
| JSON / X-Amz-Target | SQS, Secrets Manager, DynamoDB | `X-Amz-Target` request header | `registerJsonTargetStub` |
| XML / Form URL      | SNS (legacy SDK v1)            | `Action` form body parameter  | `registerXmlFormStub`    |
| REST path           | S3                             | HTTP method + URL path regex  | `registerRestStub`       |

Check the AWS SDK v2 source or Smithy model for your target service to confirm which protocol it uses.

---

## 2. Create the Gradle subproject

```
cloudmock-myservice/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/io/cloudmock/myservice/CloudMockMyServiceService.java
    │   └── resources/META-INF/services/io.cloudmock.core.spi.CloudMockService
    └── test/
        └── java/io/cloudmock/myservice/CloudMockMyServiceTest.java
```

```groovy
// cloudmock-myservice/build.gradle
dependencies {
    compileOnly project(':cloudmock-core')      // SPI only — not bundled in the module JAR
    testImplementation project(':cloudmock-core')
    testImplementation 'software.amazon.awssdk:myservice:2.25.70'
}
```

`compileOnly` keeps `cloudmock-core` off the module's runtime classpath. The core engine loads the module at runtime,
not the other way around.

Register the new subproject in `settings.gradle`:

```groovy
include 'cloudmock-myservice'
```

---

## 3. Implement `CloudMockService`

```java
package io.cloudmock.myservice;

import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

public class CloudMockMyServiceService implements CloudMockService {

    @Override
    public String serviceId() {
        return "myservice"; // (1)!
    }

    @Override
    public void register(StubRegistrar registrar) {
        registrar.registerJsonTargetStub(
            "MyService.DescribeWidget",  // (2)!
            DESCRIBE_WIDGET_RESPONSE
        );
        // register one stub per operation
    }

    private static final String DESCRIBE_WIDGET_RESPONSE = """
        {"Widget":{"WidgetId":"{{jsonPath request.body '$.WidgetId'}}","Status":"ACTIVE"}}
        """; // (3)!
}
```

1. Lowercase identifier used in logging and fault injection annotations (e.g.
   `@SimulateThrottle(service = "myservice")`).
2. The full `X-Amz-Target` header value. Find it in the SDK's generated request class or by capturing a real SDK call
   with a proxy.
3. A Handlebars template. The engine evaluates it at request time for each incoming call.

---

## 4. Write response templates

Templates are [Handlebars](https://handlebarsjs.com/) strings evaluated per request. CloudMock provides helpers on top
of WireMock's built-in set.

### Built-in WireMock helpers

```
{{randomValue type='UUID'}}               → fresh UUID per request
{{jsonPath request.body '$.FieldName'}}   → extract a field from the JSON request body
{{now}}                                   → current timestamp
```

### CloudMock custom helpers

```
{{md5 'some string'}}                     → MD5 hex digest (used for SQS message checksums)
{{md5 (jsonPath request.body '$.Body')}}  → MD5 of an extracted request field
```

### Example: echo back an identifier

```java
// Request body: {"QueueName": "my-queue"}
// Response:
private static final String CREATE_QUEUE = """
        {"QueueUrl":"http://localhost/000000000000/{{jsonPath request.body '$.QueueName'}}"}
        """;
```

### Example: generate a stable ARN

```java
// Request body: {"Name": "my-secret"}
private static final String CREATE_SECRET = """
        {
          "ARN": "arn:aws:myservice:us-east-1:000000000000:widget/{{jsonPath request.body '$.Name'}}",
          "Name": "{{jsonPath request.body '$.Name'}}",
          "VersionId": "{{randomValue type='UUID'}}"
        }
        """;
```

!!! tip "Validate your templates"
Use the WireMock standalone JAR locally to test Handlebars templates interactively before adding them to your module.
The `{{jsonPath}}` syntax is the same.

---

## 5. Register via `META-INF/services`

Create the file `src/main/resources/META-INF/services/io.cloudmock.core.spi.CloudMockService` containing the fully
qualified class name of your implementation:

```
io.cloudmock.myservice.CloudMockMyServiceService
```

This is standard Java `ServiceLoader` registration. When the module JAR is on the classpath, `CloudMock.start()`
discovers and calls `register()` automatically.

---

## 6. Write the module test

Test your module by driving real AWS SDK v2 clients against a live `CloudMock` instance. The goal is to verify that the
SDK can parse your responses without error — not to reproduce AWS semantics.

```java
class CloudMockMyServiceTest {

    static CloudMock cloudMock;
    static MyServiceClient client;

    @BeforeAll
    static void start() {
        cloudMock = new CloudMock().withService(new CloudMockMyServiceService()); // (1)!
        cloudMock.start();
        client = MyServiceClient.builder()
            .endpointOverride(URI.create("http://localhost:" + cloudMock.port()))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();
    }

    @AfterAll
    static void stop() {
        client.close();
        cloudMock.stop();
    }

    @Test
    void describeWidgetReturnsNonNullId() {
        DescribeWidgetResponse response = client.describeWidget(b -> b.widgetId("w-123"));
        assertNotNull(response.widget().widgetId());
    }
}
```

1. Use `.withService()` in module tests rather than relying on `ServiceLoader`. Classpath structure inside the Gradle
   multi-project build may prevent auto-discovery during module-level test runs.

---

## 7. Module isolation

A module **must not** depend on another module at compile or runtime. All inter-module calls go through the core SPI.
This is enforced by Gradle:

```groovy
// This will fail the build:
dependencies {
    implementation project(':cloudmock-sqs')   // ← forbidden in a service module
}
```

`testImplementation` is exempt — integration tests may combine multiple modules.

---

## 8. Exposing CLI commands via the REST API

`CloudMockService` registers the AWS wire-protocol stubs your module serves on the mock port. A
module may *also* expose a small REST surface under `/api/<serviceId>/…` by implementing the
optional `CloudMockApiService` SPI. This is what the [CLI](cli.md) drives: each route you register
advertises a command name and parameters in `/api/status`, and the CLI turns it into
`clm <serviceId> <command>` automatically — no change to the CLI is needed.

`CloudMockApiService` depends only on core SPI types (no WireMock, no AWS SDK, no picocli). Handlers
return an `ApiResponse(statusCode, body)` whose `body` map is serialised to JSON. Parameters arrive
as query-string values via `ApiRequest.queryParams()`; the request body is not read.

```java
package io.cloudmock.myservice;

import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.restapi.ApiParam;
import io.cloudmock.core.spi.restapi.CloudMockApiContext;

import java.util.List;
import java.util.Map;

public class CloudMockMyServiceApiService implements CloudMockApiService {

    @Override
    public String serviceId() {
        return "myservice"; // must match CloudMockService.serviceId()
    }

    @Override
    public void registerRoutes(CloudMockApiContext context) {
        // context.stateStore() is the same store the module's stubs use — read/write it here to
        // return live data instead of synthetic responses.
        var r = context.registrar();
        r.register(
            HttpMethod.POST,                                  // HTTP method
            "/describe-widget",                               // path under /api/myservice
            "describe-widget",                                // CLI command name
            "Describe a widget",                              // help text
            List.of(new ApiParam("id", true, "Widget id")),  // params → CLI options
            req -> new io.cloudmock.core.spi.restapi.ApiResponse(200, Map.of(
                "id", req.queryParams().getOrDefault("id", ""),
                "status", "ACTIVE")));
    }
}
```

Register it alongside the stub service with a second `ServiceLoader` file,
`src/main/resources/META-INF/services/io.cloudmock.core.spi.CloudMockApiService`:

```
io.cloudmock.myservice.CloudMockMyServiceApiService
```

Now `clm myservice describe-widget --id w-123` works against any standalone instance that has the
module loaded. Routes (and therefore CLI commands) for a module that is disabled with `--modules`
are not registered, keeping the stub view and the API view consistent.

!!! note "State-backed or synthetic — your call"
    Handlers receive the shared `StateStore` via `context.stateStore()`, so they can read and write the
    same data as the module's stubs (use the same key scheme on both surfaces so they can't drift).
    `CloudMockSqsApiService` does this — a message sent through the AWS SDK is returned by its
    `receive-message` route. A handler that ignores the store simply stays synthetic.

---

## Reference implementations

| Module                     | Protocol used       | Reference for                                             |
|----------------------------|---------------------|-----------------------------------------------------------|
| `cloudmock-sqs`            | JSON / X-Amz-Target | Header-matched stubs, UUID + MD5 helpers, array responses |
| `cloudmock-secretsmanager` | JSON / X-Amz-Target | ARN construction, nested JSON responses                   |
| `cloudmock-sns`            | XML / Form URL      | `Action`-matched stubs, XML responses                     |
| `cloudmock-s3`             | REST path           | HTTP method + path-regex stubs, XML responses             |

For `CloudMockApiService` (§8), `CloudMockSqsApiService`, `CloudMockS3ApiService`, and
`CloudMockSecretsManagerApiService` are the reference implementations.

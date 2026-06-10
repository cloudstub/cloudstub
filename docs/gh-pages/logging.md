# Logging

CloudMock uses SLF4J as its logging facade. No logging implementation is bundled — you bring your own.

## Default output (standalone mode)

In standalone mode the JAR ships `slf4j-simple`, which writes all log output to stdout. The default level is **INFO**.

Startup produces output similar to:

```
INFO CloudMock - CloudMock started on port 4566
INFO ModuleInitializer - Registered module: sqs (8 stub(s))
INFO ModuleInitializer - Registered module: sns (42 stub(s))
INFO ModuleInitializer - Registered module: secretsmanager (5 stub(s))
INFO ModuleInitializer - Registered module: s3 (107 stub(s))
```

Each matched request is logged at INFO:

```
INFO CloudMockResponseTransformer - sqs AmazonSQS.SendMessage -> 200
```

Fault-injected requests include a tag:

```
INFO CloudMockResponseTransformer - sqs AmazonSQS.SendMessage -> 400 [throttled]
INFO CloudMockResponseTransformer - sqs AmazonSQS.SendMessage -> 200 [timeout]
```

Unmatched requests are logged at WARN with the method, URL, `X-Amz-Target`, and `Content-Type` headers:

```
WARN CloudMockResponseTransformer - Unmatched request: POST / (X-Amz-Target: AmazonSQS.UnknownOp, Content-Type: application/x-amz-json-1.0)
```

Shutdown produces:

```
INFO CloudMock - CloudMock stopped
```

## Log levels

| Level | What is logged |
|-------|----------------|
| INFO  | Startup (port), shutdown, per-module registration with stub count, every matched request (service, operation, status code) |
| DEBUG | Every stub registered by `WireMockStubRegistrar`; full request and response bodies for every matched request |
| WARN  | Every unmatched request (method, URL, `X-Amz-Target`, `Content-Type`) |

## Enable DEBUG logging

### Standalone mode

Pass the `cloudmock.debug` system property or set the `CLOUDMOCK_DEBUG` environment variable before the first logger
is acquired. Both promote the root level from INFO to DEBUG.

=== "System property"

    ```
    java -Dcloudmock.debug=true -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

=== "Environment variable"

    ```
    CLOUDMOCK_DEBUG=true java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
    ```

### Embedded mode

Configure DEBUG on your own logging implementation. For Logback, add a logger entry to `logback-test.xml`:

```xml
<logger name="io.cloudmock" level="DEBUG"/>
```

## Custom logging implementation

In standalone mode you can replace `slf4j-simple` with any SLF4J-compatible implementation by placing its JAR on the
classpath ahead of the standalone JAR. In embedded mode, add your preferred implementation to your test dependencies —
CloudMock will bind to it automatically.

### Logback example (embedded / Spring Boot)

Add the dependency (Spring Boot users: already present via `spring-boot-starter`):

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <scope>test</scope>
</dependency>
```

Configure a logger in `src/test/resources/logback-test.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <logger name="io.cloudmock" level="INFO"/>

  <root level="WARN">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

Change `level="INFO"` to `level="DEBUG"` to capture stub registration and full request/response bodies.

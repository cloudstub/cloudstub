# Troubleshooting & Known Issues

Solutions for common problems, documented limitations, and where to get help. If your issue is not
covered here, search the [issue tracker](https://github.com/cloudstub/cloudstub/issues) before
filing a new report.

!!! tip "First step for any problem: enable DEBUG logging"

    DEBUG logging prints every registered stub, the full request and response bodies for matched
    requests, and a WARN line for every unmatched request. It resolves most integration problems on
    its own. See [Enable DEBUG logging](#enable-debug-logging) below and the full
    [Logging guide](logging.md).

## Known bugs in current versions

CloudStub is in the `0.1.0` beta series. The bugs below are tracked and scheduled for a fix; each
lists a workaround where one exists.

!!! bug "Codegen emits a `cloudMock` field and defaults the core version to `-SNAPSHOT`"

    **Affected:** `cloudstub-codegen`, `0.1.0` beta series &nbsp;·&nbsp;
    [#212](https://github.com/cloudstub/cloudstub/issues/212)

    Generated test skeletons use a field named `cloudMock`, and the generated `build.gradle` pins
    `cloudstub-core` at `0.1.0-SNAPSHOT` when `--core-version` is not supplied. A `-SNAPSHOT` version
    does not resolve from Maven Central.

    **Workaround:** always pass an explicit published version, for example
    `--core-version 0.1.0`, and rename the generated field to `cloudStub` during the
    [manual review](codegen.md#manual-review-steps).

!!! bug "S3 virtual-hosted-style addressing requires the client-side interceptor"

    **Affected:** `cloudstub-s3` &nbsp;·&nbsp;
    [#196](https://github.com/cloudstub/cloudstub/issues/196)

    A vanilla AWS SDK v2 `S3Client` uses virtual-hosted-style addressing
    (`https://{bucket}.s3.amazonaws.com/...`). CloudStub matches path-style
    (`http://localhost:4566/{bucket}/{key}`). The module ships an `ExecutionInterceptor` that rewrites
    virtual-hosted requests to path-style, but it is discovered only when `cloudstub-s3` is on the
    application classpath as a test dependency. A module loaded only via the JUnit extension's
    `withModules("s3")` (a downloaded, server-side-only jar) does not supply the interceptor.

    **Workaround:** declare `cloudstub-s3` as a test dependency (its stubs are then discovered
    automatically, so it does **not** go in `withModules(...)`), or set `pathStyleAccessEnabled(true)`
    on the `S3Client`. See the [S3 guide](services/s3.md).

!!! bug "An S3 bucket named like a Lambda API date prefix is shadowed by Lambda"

    **Affected:** `cloudstub-s3` + `cloudstub-lambda` in the same server &nbsp;·&nbsp;
    [#201](https://github.com/cloudstub/cloudstub/issues/201)

    S3 and Lambda both route on URL path. Routing is resolved by stub specificity, so Lambda's literal
    `/2015-03-31/functions/...` prefixes win over S3's catch-all object patterns, and every normal S3
    bucket is unaffected. The one exception: an S3 bucket named exactly like a Lambda API date prefix
    (`2015-03-31`, `2016-08-19`, `2017-03-31`) with a key path matching a Lambda route is shadowed by
    the Lambda stub.

    **Workaround:** use a bucket name that is not one of those date strings.

## Common configuration issues

### JVM / Java version

CloudStub requires **Java 17 or newer** (both the runtime and, for the codegen, the build). On an
older JVM you will see `UnsupportedClassVersionError` at startup or a compile error such as
`invalid target release: 17`.

```
java -version   # must report 17 or higher
```

Set `JAVA_HOME` to a JDK 17+ installation, or configure your build's toolchain to a matching Java
version.

### Dependency conflicts (Gradle / Maven)

CloudStub shades WireMock and Jetty inside `cloudstub-core` under `io.cloudstub.shaded.*`, so it does
**not** conflict with your own Jetty, Jackson, or a Spring Boot BOM. If you hit a version conflict, it
is almost never with a shaded dependency.

- Match your **AWS SDK v2** version across all clients. CloudStub does not pin the SDK for you; use a
  single BOM (`software.amazon.awssdk:bom`) so every `sqs`, `s3`, `dynamodb`, and so on resolves to
  the same version.
- Add `cloudstub-testing` (which pulls in `cloudstub-core` and the JUnit extension) plus only the
  service modules you use. See [Getting Started](getting-started.md#1-add-dependencies).

### Port conflicts

Standalone mode binds two ports: **4566** for the AWS mock and **4567** for the REST API. A
`BindException: Address already in use` on startup means another process holds one of them.

Override both (CLI flag takes precedence over the environment variable):

=== "CLI flags"

    ```
    java -jar cloudstub-local.jar --port=15566 --api-port=15567 --services=sqs
    ```

=== "Environment variables"

    ```
    CLOUDSTUB_PORT=15566 CLOUDSTUB_API_PORT=15567 java -jar cloudstub-local.jar --services=sqs
    ```

Remember to point your client at the new port: `AWS_ENDPOINT_URL=http://localhost:15566`. In embedded
mode, CloudStub picks a free port automatically and publishes it as the `aws.endpoint-url` system
property, so port conflicts do not arise.

### Standalone mode starts but serves nothing

Services are **opt-in**. Starting the server with no selection logs a warning and loads nothing:

```
java -jar cloudstub-local.jar --services=sqs,s3,dynamodb
```

or set `CLOUDSTUB_SERVICES=sqs,s3`. Naming an unknown service fails fast. The resolved plugin
directory and the loaded modules are printed at startup, so check that line first. See
[Standalone Mode](standalone.md).

!!! note "Auto-download and offline runs"

    A service named via `--services` whose jar is not in the plugin directory is fetched from Maven
    Central, checksum-verified, and cached in the plugin directory. If the network is unreachable and
    no jar is cached, startup fails fast naming the service and how to supply the jar manually. For
    air-gapped runs, pre-populate the plugin directory and pass `--no-download` (or
    `CLOUDSTUB_AUTO_DOWNLOAD=false`).

### Logging configuration

CloudStub uses SLF4J with no bundled implementation in embedded mode (standalone ships
`slf4j-simple`). If you see the SLF4J "no providers were found" notice in a test, add a logging
implementation (Logback is already present via `spring-boot-starter`) to your test dependencies. See
[Logging](logging.md).

## Integration troubleshooting

### Spring Boot

- **Jetty version conflict:** cannot happen through the published artifacts. WireMock and Jetty are
  shaded, so a `spring-boot-dependencies` BOM does not clash. See
  [Spring Boot Integration](spring-boot.md).
- **The SDK does not hit CloudStub:** confirm the client reads the endpoint. CloudStub sets the
  `aws.endpoint-url` system property; an `S3Client`/`SqsClient` built before CloudStub starts, or one
  with a hard-coded `endpointOverride`, will not pick it up. Build clients inside the test, or wire the
  endpoint through configuration.

### JUnit extension

- **State leaks between tests:** the extension resets fault injection after every test method, but
  service **state** (queues, items, secrets) persists for the lifetime of the CloudStub instance. Use
  a fresh instance per class, or reset explicitly between tests. See
  [JUnit Extension](junit-extension.md).
- **`withModules("s3")` behaves oddly:** S3 needs the client-side interceptor and therefore a
  test-scope dependency, not a downloaded module. See the
  [S3 known bug](#known-bugs-in-current-versions) above.
- **Fault annotations do nothing:** `@SimulateThrottle`, `@SimulateTimeout`, and
  `@SimulateNetworkBrownout` name a `service`; the id must match a loaded module (for example
  `service = "sqs"`). See [Fault Injection](fault-injection.md).

### AWS SDK v1

The `cloudstub-sdk-v1` companion redirects the **endpoint** of an SDK v1 client to CloudStub; it does
**not** translate protocols. First-party modules target the SDK v2 protocol shape. An SDK v1 client
whose wire protocol differs from v2 (for example a service whose v1 and v2 request encodings diverge)
will connect but may fail to parse the response. The signing region and credentials on a v1 client are
inert: CloudStub does not verify signatures. See [SDK v1 Support](sdk-v1.md).

### Multi-service setups

- REST-path modules (**S3**, **Lambda**) share one URL space; routing is resolved by stub specificity.
  The only collision is the [Lambda-date-prefix bucket name](#known-bugs-in-current-versions) noted
  above.
- State is isolated per service by key prefix. A full `POST /api/reset` clears all state;
  `POST /api/reset?service=sqs` clears only that service.

## Codegen troubleshooting

See the [Codegen guide](codegen.md) for the full workflow. Common issues:

- **Model validation errors:** run the validator before generating to see the derived service id,
  protocol, and operations without writing files:
  `./gradlew :cloudstub-codegen:validate --args="--model <path>"` (or `--validate` on the JAR).
- **`http://` model URL rejected:** use `https://`. `--model` must be a single `.smithy` or `.json`
  file, not a directory.
- **Generation needs Java 17+:** the codegen fails to build or run on an older JVM.
- **`-SNAPSHOT` core version in the generated `build.gradle`:** pass `--core-version 0.1.0`
  explicitly (see the [#212 known bug](#known-bugs-in-current-versions)).
- **Generated responses do not parse:** templates are **minimal placeholders** with a
  `{{! REVIEW REQUIRED ... }}` header. The SDK may require additional fields. Enrich each template from
  the SDK's response shape, as described in the
  [manual review steps](codegen.md#manual-review-steps).
- **Module is not discovered at runtime:** confirm the generated
  `META-INF/services/io.cloudstub.core.spi.CloudStubService` names the generated class and is on the
  classpath.
- **Wrong `X-Amz-Target` prefix:** `TARGET_PREFIX` is a generated guess with a `TODO`. Confirm the
  real value against a captured SDK call.

## Performance & limitations

- **State persistence file growth:** the default persistent backend (`AppendLogStateStore`) appends
  one line per mutation and compacts periodically, so a write costs the size of the change, not the
  size of the store. The legacy `JsonFileStateStore` rewrites the whole document on every write and
  suits only small or static state. See [State store](standalone.md).
- **Memory with large datasets:** state is held in memory (and mirrored to the log when persistent). A
  very large working set is bounded by heap; size the JVM accordingly.
- **Request history cap:** `GET /api/history` is bounded (default 1000 entries); raise or disable with
  `--max-history` / `CLOUDSTUB_MAX_HISTORY`.
- **SNS does not deliver messages:** `Publish` returns a `MessageId` but is not fanned out to
  subscribers. See the [SNS limitations](services/sns.md#limitations).
- **Batch operations are frequently placeholders:** several services accept batch calls without
  mutating state (see the per-service tables below).

## Service-specific known issues

Each service page has a full **Limitations** section; the most common surprises are summarized here.

| Service | Most common gaps |
|---------|------------------|
| [Lambda](services/lambda.md#limitations) | `Invoke` echoes the payload (does not execute code); versions/aliases, event source mappings, and function URLs not simulated |
| [Secrets Manager](services/secretsmanager.md#limitations) | Only the current version is tracked; `DeleteSecret` is immediate (no recovery window); `SecretBinary` not stored |
| [DynamoDB](services/dynamodb.md#limitations) | `Query` matches the partition key only; `FilterExpression`, secondary indexes, conditional writes, transactions, and pagination not applied |
| [S3](services/s3.md#limitations) | Multipart upload, versioning, and `CopyObject` are placeholders; object metadata beyond content type not stored |
| [SNS](services/sns.md#limitations) | No message delivery or fan-out; subscriptions auto-confirmed; tagging is a placeholder |
| [SQS](services/sqs.md#limitations) | Visibility timeout not enforced; FIFO treated as standard; batch operations and DLQ redrive are placeholders; Query API not supported |
| [SSM](services/ssm.md#limitations) | `SecureString` stored in plaintext; single version retained; filters and pagination not applied |

Behaviors CloudStub does **not** yet simulate at all: SQS FIFO deduplication/ordering, S3 multipart
upload lifecycle, DynamoDB conditional expressions/transactions, and IAM policy evaluation. Until a
behavior is implemented, test code that depends on it should run against real AWS.

## FAQ

**Which AWS behaviors are simulated?** CloudStub simulates the request/response contract and, for
stateful modules, live data (what you send is returned by a later read). It does not run your Lambda
code, evaluate IAM policies, or enforce quotas. See each service's **Limitations** section for the
exact boundary.

**When should I use CloudStub?** For fast, containerless JVM integration tests where you assert on
what your code did against AWS (the order reached the queue, the item was written), not on AWS
internals. For behavior CloudStub explicitly does not simulate, test against real AWS.

**How do I reset state between tests or runs?** `POST /api/reset` clears all state and request
history; `POST /api/reset?service=sqs` clears one service. In embedded mode, a fresh CloudStub
instance per test class is the simplest reset. State is in-memory by default and lost on stop unless a
store directory is configured.

**How do I debug a request that is not matching?** Enable DEBUG logging and watch for the
`WARN Unmatched request` line: it prints the method, URL, `X-Amz-Target`, and `Content-Type`, which
tells you whether the service module is loaded and which stub should have matched. The REST API
(`GET /api/history`) and the [Console](console.md) show recent requests as well.

## Getting help

### Collect debug information

Before filing an issue, gather:

1. CloudStub version (`java -jar cloudstub-local.jar --version`, or the dependency version in your
   build).
2. Java version (`java -version`) and the AWS SDK v2 version.
3. DEBUG logs covering the failing request (see below).
4. A minimal reproduction: the client call and the observed vs expected response.

### Enable DEBUG logging

=== "Standalone"

    ```
    java -Dcloudstub.debug=true -jar cloudstub-local.jar --services=sqs
    ```

    or `CLOUDSTUB_DEBUG=true`.

=== "Embedded (Logback)"

    ```xml
    <logger name="io.cloudstub" level="DEBUG"/>
    ```

See [Logging](logging.md) for the full level reference.

### File an issue

Report bugs and request features on the
[GitHub issue tracker](https://github.com/cloudstub/cloudstub/issues), using the bug, feature, or
documentation issue form. Include the debug information above and a minimal reproduction.

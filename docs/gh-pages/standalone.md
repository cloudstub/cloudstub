# Standalone Mode

Standalone mode runs CloudStub as a long-lived process instead of inside a JUnit test. Start it once, leave it running,
and point your application at `http://localhost:4566`.

## When to use it

| Use case                                          | Recommended mode                                                  |
|---------------------------------------------------|-------------------------------------------------------------------|
| JUnit integration tests                           | [Embedded mode](getting-started.md) via `CloudStubExtension`      |
| `./gradlew bootRun` or other long-lived local dev | **Standalone mode**                                               |
| Docker Compose local environment                  | **Standalone mode**                                               |
| CI pipeline tests                                 | [Embedded mode](getting-started.md) — faster, no external process |

## Distribution model

The standalone server is a thin runtime: the launcher plus `cloudstub-core`. **No service modules are bundled.**
Service modules ship as separate jars that the launcher loads at runtime from a **plugin directory**. The model is:
download the server jar once, then download the module jars you want and drop them in the plugin directory.

- `--modules-dir` controls what is **available** — which module jars are on the classpath.
- `--services` (below) narrows what is **enabled** among those.

## Build the fat JAR

```
./gradlew :cloudstub-local:shadowJar
```

This produces `cloudstub-local/build/libs/cloudstub-local.jar` — the CloudStub engine (with shaded
WireMock/Jetty) and the launcher, and nothing else. Each service module (`cloudstub-sqs`, `cloudstub-sns`,
`cloudstub-secretsmanager`, `cloudstub-s3`) is a separate jar built by its own module.

## Add the modules you want

The launcher loads every `.jar` in the plugin directory (default `./modules`):

```
mkdir -p modules
cp path/to/cloudstub-sqs.jar path/to/cloudstub-s3.jar modules/
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs,s3
```

=== "Custom plugin directory (CLI flag)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --modules-dir=/opt/cloudstub/modules --services=sqs
    ```

=== "Custom plugin directory (environment variable)"

    ```
    CLOUDSTUB_MODULES_DIR=/opt/cloudstub/modules java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs
    ```

Plugin directory precedence: `--modules-dir` flag → `CLOUDSTUB_MODULES_DIR` env var → default `./modules`. An
explicitly provided `--modules-dir` that does not exist fails fast with a clear error; a missing or empty default
`./modules` is **not** fatal — the server starts and serves nothing.

## Auto-download modules

You usually do **not** need to copy module jars by hand. Auto-download is **on by default**: when you declare a
service with `--services` (or `CLOUDSTUB_SERVICES`) and its jar is not already in the plugin directory, the launcher
fetches `io.github.cloudstub:cloudstub-<service>:<version>` from Maven Central, verifies it, writes it into the
plugin directory, and loads it. `--services` becomes the single source of truth — declare what you want and it
appears:

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs
```

- **Version:** defaults to the running `cloudstub-core` version, so a downloaded module matches the SPI the core
  provides. Override with `--module-version=<v>` or `CLOUDSTUB_MODULE_VERSION`. (Development builds are `-SNAPSHOT`,
  which is not published to Central — point `--module-version` at a released version such as `0.1.0-beta.1`.)
- **Cache:** the plugin directory is the cache. A jar that is already present is **never** re-downloaded, so the
  next start is offline-fast. When no `--modules-dir` is set and a download is needed, the default `./modules`
  directory is created to hold it.
- **Integrity:** every download is checksum-verified (the strongest published of SHA-512 / SHA-256 / SHA-1) before
  the jar is trusted and loaded. A mismatch fails the start.
- **Source:** the canonical Maven Central host. Point at an internal mirror with `--maven-base-url=<url>` or
  `CLOUDSTUB_MAVEN_BASE_URL` (a single Maven-layout repository root — not a general multi-repository resolver).

### Disable auto-download (offline / air-gapped)

Turn auto-download off with `--no-download` or `CLOUDSTUB_AUTO_DOWNLOAD=false`. A declared service whose jar is then
missing fails fast rather than reaching the network:

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs --no-download
```

```
[CloudStub] Unknown service(s): sqs. Available: (none)
[CloudStub]          Auto-download is disabled. Drop the module jar into the plugin directory, or enable
[CloudStub]          auto-download (omit --no-download / set CLOUDSTUB_AUTO_DOWNLOAD=true).
```

A failed download (offline, unknown service, HTTP error) likewise fails fast, naming the service and the coordinate
attempted and telling you how to supply the jar manually.

## Start the server

=== "Default port (4566)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

=== "Custom port (CLI flag)"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --port=9000
    ```

=== "Custom port (environment variable)"

    ```
    CLOUDSTUB_PORT=9000 java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

Port resolution precedence: `--port` flag → `CLOUDSTUB_PORT` env var → default `4566`.

## Select which services to enable

Services are **opt-in**: with no selection the server starts but loads nothing and prints a warning. Pass a
comma-separated list of service IDs to enable them. Services not listed are not registered and will not serve any
request.

=== "CLI flag"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs,secretsmanager
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_SERVICES=sqs,secretsmanager java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

Service selection precedence: `--services` flag → `CLOUDSTUB_SERVICES` env var → nothing enabled.

If you start with no `--services`, the server warns you and tells you how to enable services:

```
[CloudStub] WARNING: no services enabled — the mock will serve nothing.
[CloudStub]          Enable services with --services=<id>[,<id>...] or CLOUDSTUB_SERVICES=<id>[,<id>...].
[CloudStub]          Available services: sqs, sns, secretsmanager, s3
```

### Limit retained request history

The REST API records served requests for `GET /api/history`. In a long-lived process this journal is
capped at the last 1000 entries by default. Override the cap, or remove it entirely:

=== "CLI flag"

    ```
    java -jar cloudstub-local/build/libs/cloudstub-local.jar --max-history=5000
    ```

=== "Environment variable"

    ```
    CLOUDSTUB_MAX_HISTORY=5000 java -jar cloudstub-local/build/libs/cloudstub-local.jar
    ```

History cap precedence: `--max-history` flag → `CLOUDSTUB_MAX_HISTORY` env var → default `1000`.
Pass `unlimited` (or `none`) to retain every request.

If you name a service that is not on the classpath, the server fails fast with a clear error instead of starting up with a
silently missing service:

```
[CloudStub] Unknown service(s): dynamo. Available: sqs, sns, secretsmanager, s3
```

### Expected startup output

```
[CloudStub] Plugin directory: /path/to/modules
[CloudStub] Available services: sqs, sns, secretsmanager, s3
[CloudStub] Enabled services: sqs, secretsmanager
[CloudStub] State storage: persistent (.cloudstub)
[CloudStub] Request history: last 1000 entries
CloudStub started on port 4566
CloudStub API on port 4567
```

The **Plugin directory** line shows where module jars are loaded from; the **Available** line lists every service
discovered in that directory; the **Enabled** line lists the ones actually serving requests. If a stub is not being
served, check that its module jar is in the plugin directory (Available) and that its service appears on the Enabled
line.

On a first run, any module fetched by [auto-download](#auto-download-modules) prints a line before the summary,
naming the coordinate, version, and destination, so downloaded jars are distinguished from pre-present ones:

```
[CloudStub] Downloaded io.github.cloudstub:cloudstub-sqs:0.1.0-beta.1 -> /path/to/modules/cloudstub-sqs-0.1.0-beta.1.jar
```

The REST API is available at `http://localhost:4567` — see [REST API](rest-api.md) for the full reference, drive
the instance from the terminal with the [CLI](cli.md) (`clm` / `cloudstub`), or inspect it visually in the browser
with the [Console](console.md).

## Point an application at CloudStub

In **embedded mode** no endpoint configuration is needed: `CloudStubExtension` sets the
`aws.endpoint-url` system property to its embedded port before any AWS client is built, so SDK v2 clients in the
test JVM are redirected automatically. In **standalone mode** CloudStub is a separate process, so the application
must be told where it is. Three forms supply the endpoint — pick by how the client is built:

| Form                                    | Applies when                                                                                       |
|-----------------------------------------|----------------------------------------------------------------------------------------------------|
| `AWS_ENDPOINT_URL` environment variable | Any AWS SDK v2 client — the SDK reads it automatically, no code change.                             |
| `aws.endpoint-url` Spring property      | A Spring Boot app whose client beans read the property (see [Spring Boot](spring-boot.md)).         |
| `endpointOverride(...)` on the builder  | A hand-built client, or anywhere you need explicit per-client control.                              |

### Environment variable (no code change)

AWS SDK v2 reads `AWS_ENDPOINT_URL` on its own, so this works without touching the client code:

```
export AWS_ENDPOINT_URL=http://localhost:4566
./gradlew bootRun
```

### Manual override on the client builder

```java
SqsClient sqs = SqsClient.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();
```

For the Spring property form, the `local` / `prod` profiles, an IDE run setup, and the resolution precedence
between these forms, see [Pointing an application at CloudStub](spring-boot.md#pointing-an-application-at-cloudstub).

## Stop the server

Press `Ctrl-C` or send `SIGTERM`. CloudStub prints a shutdown message and exits cleanly — no stack trace:

```
^C
[CloudStub] Shutting down...
```

## Logging

By default, CloudStub writes INFO-level output to stdout via `slf4j-simple`. To enable DEBUG output (stub
registration and full request/response bodies), pass `-Dcloudstub.debug=true` or set `CLOUDSTUB_DEBUG=true`. See
[Logging](logging.md) for the full reference, log levels, and how to plug in a custom implementation.

## Available service modules

Each module is a separate jar; drop the ones you want into the plugin directory:

| Module                     | Service         | Protocol            |
|----------------------------|-----------------|---------------------|
| `cloudstub-sqs`            | SQS             | JSON / X-Amz-Target |
| `cloudstub-sns`            | SNS             | XML / Form URL      |
| `cloudstub-secretsmanager` | Secrets Manager | JSON / X-Amz-Target |
| `cloudstub-s3`             | S3              | REST path           |

!!! note "Out-of-scope behaviours"
    CloudStub does not simulate IAM, DynamoDB conditional expressions, SQS FIFO ordering, or S3 multipart upload
    lifecycle. See the [architecture overview](index.md#how-it-works) for the full list.

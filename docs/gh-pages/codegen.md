# CloudMock Codegen

CloudMock Codegen takes a Smithy service model as input and produces a complete `CloudMockService` implementation — the Java class, Handlebars response templates, and the `META-INF/services` registration file.

## When to use it

Use CloudMock Codegen when you need to add support for an AWS service that has a Smithy model. For services with only a handful of operations, writing the module by hand (following the [Module Authoring Guide](module-authoring.md)) may be faster. Codegen pays off for services with many operations (20+).

## Running the codegen

The codegen ships as an executable fat JAR. Build it once, then run it against a model:

```
./gradlew :cloudmock-codegen:shadowJar

java -jar cloudmock-codegen/build/libs/cloudmock-codegen.jar \
    --model <path-or-url> \
    [--output <dir>] \
    [--core-version <version>]
```

| Flag | Required | Default | Description |
|---|---|---|---|
| `--model` | yes | — | Path or `https://` URL to a single Smithy model file (`.smithy` IDL or `.json` AST) |
| `--output` | no | `./<module-name>` | Directory to write the generated module into |
| `--core-version` | no | `0.1.0-SNAPSHOT` | `cloudmock-core` version pinned in the generated `build.gradle` |

**Example:**

```
java -jar cloudmock-codegen/build/libs/cloudmock-codegen.jar \
    --model models/kinesis.smithy \
    --output cloudmock-kinesis
```

The `--model` argument accepts:

- A local `.smithy` IDL file (e.g. downloaded from the [AWS SDK v2 Smithy models](https://github.com/aws/aws-sdk-java-v2/tree/master/services))
- A local `.json` Smithy AST model
- An `https://` URL pointing directly at a `.smithy` or `.json` file — the model is downloaded to a temporary file and used as-is

Constraints:

- `http://` URLs are rejected — use `https://`.
- `--model` must be a single file (not a directory) and the name must end in `.smithy` or `.json`.

## What the codegen produces

The codegen writes a complete, compilable module skeleton:

- `build.gradle` — `compileOnly`/`testImplementation` on `cloudmock-core` at the pinned `--core-version`
- The `CloudMock<Service>Service` class — one `register*Stub` call per operation, each loading its body from the classpath via a generated `loadTemplate(name)` helper
- `META-INF/services/io.cloudmock.core.spi.CloudMockService` — registers the generated class for `ServiceLoader` discovery
- One `src/main/resources/templates/<Operation>.hbs` template per operation
- A `CloudMock<Service>ServiceTest` skeleton with one stubbed `@Test` per operation

Response templates are **minimal placeholders**, not finished responses. Each `.hbs` file opens with a `{{! REVIEW REQUIRED ... }}` comment listing the operation's output shape and its members, so you know what to fill in.

**Example `register()` for a two-operation JSON service:**

```java
@Override
public void register(StubRegistrar registrar) {
    registrar.registerJsonTargetStub(TARGET_PREFIX + "CreateStream",   loadTemplate("CreateStream"));
    registrar.registerJsonTargetStub(TARGET_PREFIX + "DescribeStream", loadTemplate("DescribeStream"));
}
```

Each operation's response body lives in its own file. For example `src/main/resources/templates/DescribeStream.hbs` (illustrative — the generated placeholder reflects the model's output shape):

```
{{! REVIEW REQUIRED — output: DescribeStreamOutput [StreamDescription: structure] }}
{"StreamDescription":{}}
```

`TARGET_PREFIX` itself is a generated guess derived from the service name and carries a `TODO` comment — confirm the real `X-Amz-Target` prefix during review (see step 1 below).

## Manual review steps

The generated output is a starting point, not a finished module. Always review and adjust:

1. **Verify the target header values.** The codegen derives them from the Smithy model, but the actual value sent by the AWS SDK may differ. Confirm by capturing a real SDK call or checking the SDK's generated request marshaller.

2. **Enrich response templates.** Generated templates return minimal responses. The SDK may require additional fields to parse the response without error. Add them based on the SDK's response POJO and its required fields.

3. **Add a module test.** The codegen does not generate tests. Write at least one test per operation following the pattern in the [Module Authoring Guide](module-authoring.md#6-write-the-module-test).

4. **Check for custom Handlebars helpers.** If a response requires an MD5 checksum or other derived field, use CloudMock's `{{md5 ...}}` helper or WireMock's built-in helpers. The codegen uses only `{{randomValue type='UUID'}}` and `{{jsonPath ...}}` by default.

5. **Enforce module isolation.** Confirm that `build.gradle` uses `compileOnly` for `cloudmock-core` and declares no dependencies on other `cloudmock-*` modules.

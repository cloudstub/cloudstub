# CloudMock Codegen

CloudMock Codegen takes a Smithy service model as input and produces a complete `CloudMockService` implementation — the Java class, Handlebars response templates, and the `META-INF/services` registration file.

## When to use it

Use CloudMock Codegen when you need to add support for an AWS service that has a Smithy model. For services with only a handful of operations, writing the module by hand (following the [Module Authoring Guide](module-authoring.md)) may be faster. Codegen pays off for services with many operations (20+).

## Running the codegen

```
./gradlew :cloudmock-codegen:run --args="<smithy-model-file> <output-dir>"
```

**Example:**

```
./gradlew :cloudmock-codegen:run \
    --args="models/kinesis.smithy cloudmock-kinesis/src/main/java"
```

The Smithy model file can be:

- A local `.smithy` file downloaded from the [AWS SDK v2 Smithy models](https://github.com/aws/aws-sdk-java-v2/tree/master/services)
- A `.json` Smithy model

## What the codegen produces

For each operation in the model the codegen generates:

1. A stub registration call in the `register()` method
2. A Handlebars response template that echoes back the primary identifier from the request (e.g. queue name, topic ARN, stream name)
3. A `META-INF/services` file pointing at the generated class

**Example output for a two-operation service:**

```java
public class CloudMockKinesisService implements CloudMockService {

    @Override
    public String serviceId() { return "kinesis"; }

    @Override
    public void register(StubRegistrar registrar) {
        registrar.registerJsonTargetStub("Kinesis_20131202.CreateStream",   CREATE_STREAM);
        registrar.registerJsonTargetStub("Kinesis_20131202.DescribeStream", DESCRIBE_STREAM);
    }

    private static final String CREATE_STREAM = "{}";

    private static final String DESCRIBE_STREAM = """
            {"StreamDescription":{"StreamName":"{{jsonPath request.body '$.StreamName'}}", \
            "StreamStatus":"ACTIVE","Shards":[]}}
            """;
}
```

## Manual review steps

The generated output is a starting point, not a finished module. Always review and adjust:

1. **Verify the target header values.** The codegen derives them from the Smithy model, but the actual value sent by the AWS SDK may differ. Confirm by capturing a real SDK call or checking the SDK's generated request marshaller.

2. **Enrich response templates.** Generated templates return minimal responses. The SDK may require additional fields to parse the response without error. Add them based on the SDK's response POJO and its required fields.

3. **Add a module test.** The codegen does not generate tests. Write at least one test per operation following the pattern in the [Module Authoring Guide](module-authoring.md#6-write-the-module-test).

4. **Check for custom Handlebars helpers.** If a response requires an MD5 checksum or other derived field, use CloudMock's `{{md5 ...}}` helper or WireMock's built-in helpers. The codegen uses only `{{randomValue type='UUID'}}` and `{{jsonPath ...}}` by default.

5. **Enforce module isolation.** Confirm that `build.gradle` uses `compileOnly` for `cloudmock-core` and declares no dependencies on other `cloudmock-*` modules.

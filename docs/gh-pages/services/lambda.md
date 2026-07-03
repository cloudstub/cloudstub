# AWS Lambda

## Overview

The `cloudstub-lambda` module mocks AWS Lambda. Lambda uses the REST JSON protocol (paths such as
`POST /2015-03-31/functions` and `POST /2015-03-31/functions/{name}/invocations`), which AWS SDK v2
drives directly.

The function lifecycle and tag operations are **state-backed**: a function created by
`CreateFunction` is returned by a later `GetFunction`, listed by `ListFunctions`, survives a restart
when a persistent store directory is configured, and is visible through the [REST API](../rest-api.md).
`Invoke` does not run code: it echoes the request payload back with status `200`, so callers get a
deterministic, inspectable result. See [Supported operations](#supported-operations) for the full
list.

## Standalone usage

Start the standalone server with the Lambda module enabled (it is auto-downloaded if not already in
the plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=lambda
```

Applications talk to it through the **AWS SDK** by pointing the client's endpoint at the mock port
(`http://localhost:4566`), see the [Test example](#test-example) for a `LambdaClient` setup and
[Standalone Mode](../standalone.md) for the full configuration.

Create a function and invoke it with the AWS CLI:

```
$ aws lambda create-function --endpoint-url http://localhost:4566 \
    --function-name processor \
    --runtime nodejs20.x --role arn:aws:iam::000000000000:role/lambda-role \
    --handler index.handler --zip-file fileb://function.zip

$ aws lambda invoke --endpoint-url http://localhost:4566 \
    --function-name processor --payload '{"order":42}' out.json
$ cat out.json
{"order":42}
```

`Invoke` returns the payload it received (the mock does not execute the function), so a client can
verify its request and response wiring end to end.

To inspect function state from the terminal, use the [CLI](../cli.md) (`clb`), or call the
[REST API](../rest-api.md) on the API port (`4567`) directly:

=== "CLI"

    ```
    $ clb lambda list-functions
    {
      "functions" : [ {
        "FunctionName" : "processor",
        "Runtime" : "nodejs20.x",
        "Handler" : "index.handler"
      } ]
    }

    $ clb lambda get-function --name processor
    {
      "configuration" : {
        "FunctionName" : "processor",
        "Runtime" : "nodejs20.x",
        "State" : "Active"
      }
    }
    ```

=== "curl"

    ```
    $ curl -s "http://localhost:4567/api/lambda/list-functions"
    {"functions":[{"FunctionName":"processor","Runtime":"nodejs20.x","Handler":"index.handler"}]}

    $ curl -s "http://localhost:4567/api/lambda/get-function?name=processor"
    {"configuration":{"FunctionName":"processor","Runtime":"nodejs20.x","State":"Active"}}
    ```

The REST API and the SDK share the same state store, so a function your application creates through
the SDK is listed by `GET /api/lambda/list-functions`. The REST surface is read-oriented; creating
and invoking functions goes through the AWS SDK. See [REST API access](#rest-api-access) for the full
route set.

## Test example

In embedded mode, add `cloudstub-lambda` (see [Getting Started](../getting-started.md)) and exercise
the service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class ProcessorFunctionTest {

    @Test
    void invokeEchoesThePayload() {
        LambdaClient lambda = LambdaClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        lambda.createFunction(b -> b.functionName("processor")
            .runtime("nodejs20.x").role("arn:aws:iam::000000000000:role/lambda-role")
            .handler("index.handler")
            .code(c -> c.zipFile(SdkBytes.fromUtf8String("exports.handler=async e=>e;"))));

        InvokeResponse response = lambda.invoke(b -> b.functionName("processor")
            .payload(SdkBytes.fromUtf8String("{\"order\":42}")));

        assertEquals(200, response.statusCode());
        assertEquals("{\"order\":42}", response.payload().asUtf8String());
    }
}
```

## REST API access

The module exposes a [REST API](../rest-api.md) under `/api/lambda/…`. These routes read the same
state as the AWS-protocol stubs, so a function created with the SDK is listed by
`GET /api/lambda/list-functions`. Parameters are passed as query-string values.

| Route                              | Parameters | Description                    |
| ---------------------------------- | ---------- | ------------------------------ |
| `GET /api/lambda/list-functions`   | —          | List functions                 |
| `GET /api/lambda/get-function`     | `name`     | Show a function's configuration |

## Supported operations

State-backed operations return live data from the shared state store:

| Operation                     | Behavior                                                            |
| ----------------------------- | ------------------------------------------------------------------ |
| `CreateFunction`              | Records the function; returns its configuration                    |
| `GetFunction`                 | Returns the stored configuration, code location, and tags          |
| `GetFunctionConfiguration`    | Returns the stored configuration                                   |
| `ListFunctions`               | Returns every created function's configuration                     |
| `UpdateFunctionConfiguration` | Merges the supplied fields into the stored configuration           |
| `UpdateFunctionCode`          | Recomputes `CodeSize`/`CodeSha256` from the new inline code        |
| `DeleteFunction`              | Removes the function and its tags                                  |
| `Invoke`                      | Echoes the request payload with `X-Amz-Executed-Version: $LATEST`  |
| `TagResource` / `UntagResource` | Adds or removes function tags                                    |
| `ListTags`                    | Returns the function's tags                                        |
| `GetAccountSettings`          | Returns fixed account limits plus the live function count          |

## Limitations

- `Invoke` does not execute the function: it returns the request payload verbatim. Function logic,
  the log tail, and the `FunctionError` field are not simulated.
- Versions and aliases are not simulated: a qualifier in the function name or ARN (for example
  `processor:PROD`) is stripped and resolves to the unqualified function.
- `CodeSize` and `CodeSha256` are derived from the inline `Code.ZipFile` bytes only. Code supplied
  from S3 or an image URI, and layers, are not stored.
- Event source mappings, function URLs, concurrency, event-invoke config, and durable executions are
  not implemented.
- `UntagResource` removes only the first `tagKeys` value in a request; removing several tags in one
  call is not simulated.
- `CreateFunction` accepts a function without validating the runtime, role, or handler; requests
  always succeed unless the function already exists (`ResourceConflictException`).
- Do not enable `lambda` and `s3` on the same server yet. Both are addressed by URL path, and S3's
  path-style routes currently shadow Lambda's paths on the shared port. Either run Lambda alongside
  the header-routed services (SQS, SNS, Secrets Manager, DynamoDB) on one server, or run Lambda and
  S3 as two separate servers on different ports (`--port`/`--api-port`, plus `--store-dir` for each)
  and point each SDK client at its own endpoint.

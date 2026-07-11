# Amazon SSM

## Overview

The `cloudstub-ssm` module mocks the Amazon SSM Parameter Store. AWS SDK v2 drives it with the
JSON / `X-Amz-Target` protocol.

The Parameter Store operations are **state-backed**: a parameter written by `PutParameter` is
returned by a later `GetParameter`, `GetParameters`, `GetParametersByPath`, or `DescribeParameters`,
survives a restart when a persistent store directory is configured, and is visible through the
[REST API](../rest-api.md). `PutParameter` assigns version 1 on create and increments the version on
an overwrite. Tags attached with `AddTagsToResource` are returned by `ListTagsForResource`. See
[Supported operations](#supported-operations) for the full list. Operations outside the Parameter
Store surface are not registered and return HTTP 404.

## Standalone usage

Start the standalone server with the SSM module enabled (it is auto-downloaded if not already in the
plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=ssm
```

Applications talk to it through the **AWS SDK** by pointing the client's endpoint at the mock port
(`http://localhost:4566`), see the [Test example](#test-example) for an `SsmClient` setup and
[Standalone Mode](../standalone.md) for the full configuration.

Store a parameter and read it back with the AWS CLI:

```
$ aws ssm put-parameter --endpoint-url http://localhost:4566 \
    --name /app/db-url --value "jdbc:postgresql://db/app" --type String

$ aws ssm get-parameter --endpoint-url http://localhost:4566 --name /app/db-url
{
    "Parameter": {
        "Name": "/app/db-url",
        "Type": "String",
        "Value": "jdbc:postgresql://db/app",
        "Version": 1,
        "ARN": "arn:aws:ssm:us-east-1:000000000000:parameter/app/db-url",
        "DataType": "text"
    }
}
```

To inspect parameter state from the terminal, use the [CLI](../cli.md) (`clb`), or call the
[REST API](../rest-api.md) on the API port (`4567`) directly. Parameters are passed as query-string
values. List and read the parameters an application wrote through the SDK:

=== "CLI"

    ```
    $ clb ssm put-parameter --name /app/db-url --value "jdbc:postgresql://db/app"
    {
      "name" : "/app/db-url",
      "version" : 1
    }

    $ clb ssm get-parameter --name /app/db-url
    {
      "parameter" : {
        "Name" : "/app/db-url",
        "Type" : "String",
        "Value" : "jdbc:postgresql://db/app",
        "Version" : 1,
        "ARN" : "arn:aws:ssm:us-east-1:000000000000:parameter/app/db-url",
        "DataType" : "text"
      }
    }
    ```

=== "curl"

    ```
    $ curl -s -X POST "http://localhost:4567/api/ssm/put-parameter?name=/app/db-url&value=jdbc:postgresql://db/app"
    {"name":"/app/db-url","version":1}

    $ curl -s "http://localhost:4567/api/ssm/get-parameter?name=/app/db-url"
    {"parameter":{"Name":"/app/db-url","Type":"String","Value":"jdbc:postgresql://db/app","Version":1,"ARN":"arn:aws:ssm:us-east-1:000000000000:parameter/app/db-url","DataType":"text"}}
    ```

The REST API and the SDK share the same state store, so a parameter your application puts through the
SDK is returned by `GET /api/ssm/get-parameter`, and one written through the REST API is returned by
the SDK. See [REST API access](#rest-api-access) for the full route set.

## Test example

In embedded mode, add `cloudstub-ssm` (see [Getting Started](../getting-started.md)) and exercise the
service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class ConfigStoreTest {

    @Test
    void parameterPutIsReadBack() {
        SsmClient ssm = SsmClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        ssm.putParameter(b -> b.name("/app/db-url").value("jdbc:postgresql://db/app"));

        assertEquals(
            "jdbc:postgresql://db/app",
            ssm.getParameter(b -> b.name("/app/db-url")).parameter().value());
    }
}
```

## REST API access

The module exposes a [REST API](../rest-api.md) under `/api/ssm/…`. These routes read and write the
same state as the AWS-protocol stubs, so a parameter put with the SDK is returned by
`GET /api/ssm/get-parameter`. Parameters are passed as query-string values.

| Route                          | Parameters             | Description                          |
| ------------------------------ | ---------------------- | ------------------------------------ |
| `GET /api/ssm/list-parameters` | —                      | List stored parameters (metadata)    |
| `GET /api/ssm/get-parameter`   | `name`                 | Get a stored parameter               |
| `POST /api/ssm/put-parameter`  | `name`, `value`, `type`| Create or overwrite a parameter      |
| `POST /api/ssm/delete-parameter`| `name`                | Delete a parameter                   |

## Supported operations

State-backed operations return live data from the shared state store:

| Operation               | Behavior                                                                  |
| ----------------------- | ------------------------------------------------------------------------- |
| `PutParameter`          | Stores a parameter; version 1 on create, incremented on overwrite. Rejects a duplicate without `Overwrite` |
| `GetParameter`          | Returns the stored parameter, or `ParameterNotFound`                      |
| `GetParameters`         | Returns the found parameters and lists the names that were not found       |
| `GetParametersByPath`   | Returns parameters under a hierarchy path (recursive or direct children)   |
| `DeleteParameter`       | Removes a parameter and its tags, or `ParameterNotFound`                   |
| `DeleteParameters`      | Removes the named parameters; reports deleted and invalid names            |
| `DescribeParameters`    | Returns metadata for every stored parameter                               |
| `GetParameterHistory`   | Returns the current version as a single history entry                     |
| `LabelParameterVersion` | Returns the parameter version; labels are not persisted                   |
| `AddTagsToResource`     | Attaches tags to a parameter                                              |
| `RemoveTagsFromResource`| Removes tags from a parameter                                             |
| `ListTagsForResource`   | Returns a parameter's tags                                               |

## Limitations

- KMS encryption is not simulated: a `SecureString` value is stored and returned in plaintext,
  regardless of `WithDecryption`.
- Only the current version of a parameter is retained. `GetParameterHistory` returns a single entry,
  and version selectors or labels in a `GetParameter` name (`name:2`, `name:label`) are not resolved.
- `LabelParameterVersion` does not persist labels; it echoes the version.
- `ParameterFilters` on `GetParametersByPath` and `DescribeParameters` are not applied, and results
  are not paginated (`MaxResults` and `NextToken` are ignored).
- Parameter policies, tiers (all reported as `Standard`), `AllowedPattern` validation, and the
  advanced-parameter size limit are not enforced.
- Tags are supported only for the `Parameter` resource type.
- Returned ARNs use a fixed region and account (`us-east-1`, `000000000000`) regardless of the
  client's configured region.
- Operations outside the Parameter Store surface (documents, maintenance windows, patch baselines,
  automation, sessions, and so on) are not registered and return HTTP 404.
```

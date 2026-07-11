# AWS Secrets Manager

## Overview

The `cloudstub-secretsmanager` module mocks AWS Secrets Manager. Its operation set is generated from
the AWS Secrets Manager Smithy model; AWS SDK v2 drives it with the JSON / `X-Amz-Target` protocol.

The core secret operations are **state-backed**: a secret created with `CreateSecret` is returned by
a later `GetSecretValue` or `DescribeSecret`, updated by `PutSecretValue` / `UpdateSecret`, removed
by `DeleteSecret`, survives a restart when a persistent store directory is configured, and is visible
through the [REST API](../rest-api.md). A `SecretId` is accepted as either a secret name or a full
ARN. The remaining operations are registered from the model and return well-formed but stateless
placeholder responses. See [Supported operations](#supported-operations) for the full list.

## Standalone usage

Start the standalone server with the Secrets Manager module enabled (it is auto-downloaded if not
already in the plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=secretsmanager
```

Applications talk to it through the **AWS SDK** by pointing the client's endpoint at the mock port
(`http://localhost:4566`) — see the [Test example](#test-example) for a `SecretsManagerClient` setup
and [Standalone Mode](../standalone.md) for the full configuration.

To inspect and drive secret state from the terminal, use the [CLI](../cli.md) (`clb`), or call the
[REST API](../rest-api.md) on the API port (`4567`) directly — for example with `curl`. Parameters
are passed as query-string values. Store a secret and read it back:

=== "CLI"

    ```
    $ clb secretsmanager put --name db-password --value s3cr3t
    {
      "arn" : "arn:aws:secretsmanager:us-east-1:000000000000:secret:db-password",
      "name" : "db-password",
      "versionId" : "7568e4c5-4484-4291-a44b-e648b8c47a26"
    }

    $ clb secretsmanager get --name db-password
    {
      "arn" : "arn:aws:secretsmanager:us-east-1:000000000000:secret:db-password",
      "name" : "db-password",
      "secretString" : "s3cr3t",
      "versionId" : "7568e4c5-4484-4291-a44b-e648b8c47a26"
    }
    ```

=== "curl"

    ```
    $ curl -s -X PUT "http://localhost:4567/api/secretsmanager/put?name=db-password&value=s3cr3t"
    {"arn":"arn:aws:secretsmanager:us-east-1:000000000000:secret:db-password","name":"db-password","versionId":"7568e4c5-4484-4291-a44b-e648b8c47a26"}

    $ curl -s "http://localhost:4567/api/secretsmanager/get?name=db-password"
    {"arn":"arn:aws:secretsmanager:us-east-1:000000000000:secret:db-password","name":"db-password","secretString":"s3cr3t","versionId":"7568e4c5-4484-4291-a44b-e648b8c47a26"}
    ```

The REST API and the SDK share the same state store, so a secret your application creates through the
SDK is returned by `GET /api/secretsmanager/get`, and vice versa. See
[REST API access](#rest-api-access) for the full route set.

## Test example

In embedded mode, add `cloudstub-secretsmanager` (see [Getting Started](../getting-started.md)) and
exercise the service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class SecretRoundTripTest {

    @Test
    void secretStoredIsRetrieved() {
        SecretsManagerClient client = SecretsManagerClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        client.createSecret(b -> b.name("db-password").secretString("s3cr3t"));

        String value = client.getSecretValue(b -> b.secretId("db-password")).secretString();

        assertEquals("s3cr3t", value);
    }
}
```

## REST API access

The module exposes a [REST API](../rest-api.md) under `/api/secretsmanager/…`. These routes read and
write the same state as the AWS-protocol stubs — a secret created with the SDK is returned by
`GET /api/secretsmanager/get`, and vice versa. Parameters are passed as query-string values (e.g.
`PUT /api/secretsmanager/put?name=db-password&value=s3cr3t`).

| Route                              | Parameters       | Description                   |
| ---------------------------------- | ---------------- | ----------------------------- |
| `GET /api/secretsmanager/list`     | —                | List secret names             |
| `GET /api/secretsmanager/get`      | `name`           | Get a secret value            |
| `PUT /api/secretsmanager/put`      | `name`, `value`  | Create or update a secret     |
| `DELETE /api/secretsmanager/delete`| `name`           | Delete a secret               |

## Supported operations

State-backed operations return live data from the shared state store:

| Operation             | Behavior                                                                  |
| --------------------- | ------------------------------------------------------------------------- |
| `CreateSecret`        | Stores the secret; returns its `ARN`, `Name`, and `VersionId`             |
| `GetSecretValue`      | Returns the stored `SecretString` for a name or ARN                       |
| `BatchGetSecretValue` | Returns the stored values for a `SecretIdList`; misses go to `Errors`     |
| `PutSecretValue`      | Replaces the value with a new version; returns the new `VersionId`        |
| `UpdateSecret`        | Updates the value and/or description; returns a new `VersionId`           |
| `DescribeSecret`      | Returns the secret's metadata (`ARN`, `Name`, `Description`, dates, tags) |
| `DeleteSecret`        | Removes the secret and its tags; returns its `ARN`, `Name`, `DeletionDate`|
| `ListSecrets`         | Returns all stored secrets                                                |
| `TagResource`         | Adds/updates tags returned by `DescribeSecret`                            |
| `UntagResource`       | Removes the named tag keys                                                 |

`GetSecretValue`, `PutSecretValue`, `UpdateSecret`, `DescribeSecret`, `DeleteSecret`, `TagResource`,
and `UntagResource` return a `ResourceNotFoundException` (HTTP 400) for a secret that does not exist.

The remaining operations are registered and return well-formed but stateless placeholder responses;
they do not read or mutate state: `CancelRotateSecret`, `DeleteResourcePolicy`, `GetRandomPassword`,
`GetResourcePolicy`, `ListSecretVersionIds`, `PutResourcePolicy`, `RemoveRegionsFromReplication`,
`ReplicateSecretToRegions`, `RestoreSecret`, `RotateSecret`, `StopReplicationToReplica`,
`UpdateSecretVersionStage`, `ValidateResourcePolicy`.

## Limitations

- Only a single current version is tracked per secret — version IDs change but prior versions are
  not retained, and `ListSecretVersionIds` is a placeholder.
- Version stages are reported as a fixed `AWSCURRENT`; `UpdateSecretVersionStage` is a placeholder.
- `DeleteSecret` removes the secret immediately — recovery windows (`RecoveryWindowInDays`,
  scheduled deletion) and `RestoreSecret` are not simulated.
- `CreateSecret` on an existing name overwrites it rather than raising `ResourceExistsException`.
- Rotation, cross-region replication, resource policies, and KMS encryption are placeholders.
- `SecretBinary` is not stored; only `SecretString` round-trips.
- `BatchGetSecretValue` honors only `SecretIdList`; the `Filters` parameter is ignored.

See also: [Troubleshooting](../troubleshooting.md) for common integration problems and workarounds.

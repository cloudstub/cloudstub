# Amazon SNS

## Overview

The `cloudstub-sns` module mocks Amazon Simple Notification Service. Its operation set is generated
from the AWS SNS Smithy model by `cloudstub-codegen`; AWS SDK v2 drives it with the AWS Query
(XML / Form) protocol, where each operation is matched by its `Action` form parameter.

The topic and subscription operations are **state-backed**: a topic created with `CreateTopic` is
returned by a later `ListTopics`, a subscription created with `Subscribe` is returned by
`ListSubscriptionsByTopic`, `GetTopicAttributes` reports the live subscription count, and the data
survives a restart when a persistent store directory is configured. State is visible through the
[REST API](../rest-api.md) and the console. The remaining operations are registered from the model and
return well-formed but stateless responses. See [Supported operations](#supported-operations) for
the full list.

SNS does **not** deliver messages: `Publish` is acknowledged with a `MessageId` but is not fanned
out to subscribers (see [Limitations](#limitations)).

## Standalone usage

Start the standalone server with the SNS module enabled (it is auto-downloaded if not already in the
plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sns
```

Point any AWS client at the mock port (`http://localhost:4566`) — see
[Standalone Mode](../standalone.md) for the full configuration. With the AWS CLI, a created topic is
returned by `list-topics` and a subscription by `list-subscriptions-by-topic`:

```
$ aws --endpoint-url=http://localhost:4566 sns create-topic --name orders
{
    "TopicArn": "arn:aws:sns:us-east-1:000000000000:orders"
}

$ aws --endpoint-url=http://localhost:4566 sns subscribe \
      --topic-arn arn:aws:sns:us-east-1:000000000000:orders \
      --protocol sqs --notification-endpoint arn:aws:sqs:us-east-1:000000000000:orders-queue
{
    "SubscriptionArn": "arn:aws:sns:us-east-1:000000000000:orders:5d41402a-..."
}

$ aws --endpoint-url=http://localhost:4566 sns list-topics
{
    "Topics": [
        {
            "TopicArn": "arn:aws:sns:us-east-1:000000000000:orders"
        }
    ]
}
```

To inspect and drive topic state from the terminal, call the [REST API](../rest-api.md) on the API port
(`4567`) — see [REST/CLI access](#restcli-access).

## Test example

In embedded mode, add `cloudstub-sns` (see [Getting Started](../getting-started.md)) and exercise the
service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class SnsTopicTest {

    @Test
    void createdTopicIsListed() {
        SnsClient sns = SnsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        String topicArn = sns.createTopic(b -> b.name("orders")).topicArn();
        sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs")
            .endpoint("arn:aws:sqs:us-east-1:000000000000:orders-queue"));

        assertTrue(sns.listTopics().topics().stream()
            .anyMatch(t -> t.topicArn().equals(topicArn)));
        assertEquals(1, sns.listSubscriptionsByTopic(b -> b.topicArn(topicArn))
            .subscriptions().size());
    }
}
```

## REST/CLI access

The module exposes a [REST API](../rest-api.md) under `/api/sns/…`, reachable from the terminal with
the [CLI](../cli.md) (`clb`) or directly with `curl`. These routes read and write the same state as
the AWS-protocol stubs — a topic created with the SDK is returned by `GET /api/sns/list-topics`, and
vice versa. Parameters are passed as query-string values.

=== "CLI"

    ```
    $ clb sns create-topic --topic orders
    {
      "topicArn" : "arn:aws:sns:us-east-1:000000000000:orders"
    }

    $ clb sns list-topics
    {
      "topics" : [ "arn:aws:sns:us-east-1:000000000000:orders" ]
    }
    ```

=== "curl"

    ```
    $ curl -s -X POST "http://localhost:4567/api/sns/create-topic?topic=orders"
    {"topicArn":"arn:aws:sns:us-east-1:000000000000:orders"}

    $ curl -s "http://localhost:4567/api/sns/list-topics"
    {"topics":["arn:aws:sns:us-east-1:000000000000:orders"]}
    ```

| Route                            | Parameters                     | Description                       |
| -------------------------------- | ------------------------------ | --------------------------------- |
| `GET /api/sns/list-topics`       | —                              | List topics                       |
| `POST /api/sns/create-topic`     | `topic`                        | Create a topic                    |
| `POST /api/sns/delete-topic`     | `topic`                        | Delete a topic and subscriptions  |
| `POST /api/sns/subscribe`        | `topic`, `protocol`, `endpoint`| Subscribe an endpoint to a topic  |
| `GET /api/sns/list-subscriptions`| `topic`                        | List a topic's subscriptions      |
| `POST /api/sns/publish`          | `topic`, `message`             | Publish (acknowledged, see below) |

The same routes drive the CLI (`cloudstub sns list-topics`, `cloudstub sns subscribe …`).

## Supported operations

State-backed operations read and write the shared state store:

| Operation                  | Behavior                                                          |
| -------------------------- | ---------------------------------------------------------------- |
| `CreateTopic`              | Records the topic; returns its ARN                               |
| `DeleteTopic`              | Removes the topic and its subscriptions                          |
| `ListTopics`               | Returns the ARNs of all created topics                           |
| `GetTopicAttributes`       | Returns attributes, including the live `SubscriptionsConfirmed`  |
| `Subscribe`                | Records the subscription; returns its ARN                        |
| `Unsubscribe`              | Removes a subscription by ARN                                    |
| `ListSubscriptions`        | Returns all subscriptions across topics                          |
| `ListSubscriptionsByTopic` | Returns a topic's subscriptions                                  |
| `GetSubscriptionAttributes`| Returns a subscription's protocol, endpoint, and topic ARN       |

The remaining operations are registered and return well-formed but stateless responses; they do not
read or mutate state: `Publish` (returns a fresh `MessageId`), `PublishBatch`, `ConfirmSubscription`,
`SetTopicAttributes`, `SetSubscriptionAttributes`, `AddPermission`, `RemovePermission`,
`TagResource`, `UntagResource`, `ListTagsForResource`, and the platform-application,
platform-endpoint, SMS-sandbox, phone-number, and data-protection-policy operations.

## Limitations

- **No message delivery.** `Publish` and `PublishBatch` return a `MessageId` but do not fan out to
  subscribers — nothing is delivered to subscribed SQS queues, HTTP endpoints, Lambda, email, or
  SMS. A published message cannot be read back from SNS (this matches AWS — SNS has no message
  store — but CloudStub also performs no delivery).
- **No subscription confirmation.** `Subscribe` records the subscription as immediately confirmed;
  `ConfirmSubscription` is a no-op and no confirmation token is sent.
- `SetTopicAttributes` and `SetSubscriptionAttributes` are placeholders: attributes returned by the
  getters are derived from stored state (e.g. subscription count) and fixed defaults, and cannot be
  changed.
- Resource tagging (`TagResource`, `UntagResource`, `ListTagsForResource`) is a placeholder.
- Platform applications/endpoints, the SMS sandbox, phone-number opt-out, and data-protection
  policies are acknowledged but not simulated.

# Amazon SQS

## Overview

The `cloudstub-sqs` module mocks Amazon Simple Queue Service. Its operation set is generated from the
AWS SQS Smithy model; AWS SDK v2 (≥ 2.20) drives it with the JSON / `X-Amz-Target` protocol.

The core queue and message operations are **state-backed**: a message sent through `SendMessage` is
returned by a later `ReceiveMessage`, survives a restart when a persistent store directory is
configured, and is visible through the [REST API](rest-api.md) and [CLI](cli.md). The remaining
operations are registered from the model and return well-formed but stateless placeholder responses.
See [Supported operations](#supported-operations) for the full list.

## Standalone usage

Start the standalone server with the SQS module enabled (it is auto-downloaded if not already in the
plugin directory):

```
java -jar cloudstub-local/build/libs/cloudstub-local.jar --services=sqs
```

Applications talk to it through the **AWS SDK** by pointing the client's endpoint at the mock port
(`http://localhost:4566`) — see the [Test example](#test-example) for an `SqsClient` setup and
[Standalone Mode](standalone.md) for the full configuration.

To inspect and drive queue state from the terminal, use the CloudStub [CLI](cli.md) (`clm`) — a thin
client over the [REST API](rest-api.md) that needs neither the AWS SDK nor the AWS CLI. With the
server running, send a message and receive it back; the generated message ID, body MD5, and receipt
handle come back as JSON:

```
$ clm sqs send-message --queue orders --body "hello"
{
  "md5OfBody" : "5d41402abc4b2a76b9719d911017c592",
  "messageId" : "f8eafb78-edcd-46f7-b78a-43b9e160cbef"
}

$ clm sqs receive-message --queue orders
{
  "messages" : [ {
    "body" : "hello",
    "receiptHandle" : "f8eafb78-edcd-46f7-b78a-43b9e160cbef",
    "messageId" : "f8eafb78-edcd-46f7-b78a-43b9e160cbef"
  } ]
}
```

The CLI and the SDK share the same state store, so a message your application sends through the SDK
is returned by `clm sqs receive-message`, and vice versa. `clm sqs list-queues` lists the queues your
application created through the SDK, and `clm sqs purge-queue --queue orders` clears a queue's
messages. See [REST and CLI access](#rest-and-cli-access) for the full command set.

## Test example

In embedded mode, add `cloudstub-sqs` (see [Getting Started](getting-started.md)) and exercise the
service end to end with `CloudStubExtension`:

```java
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CloudStubExtension.class)
class SqsRoundTripTest {

    @Test
    void messageSentIsReceived() {
        SqsClient sqs = SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty("aws.endpoint-url")))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1)
            .build();

        String queueUrl = sqs.createQueue(b -> b.queueName("orders")).queueUrl();
        sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody("hello"));

        List<Message> messages = sqs.receiveMessage(b -> b.queueUrl(queueUrl)).messages();

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).body());
    }
}
```

## REST and CLI access

The module exposes a [REST API](rest-api.md) under `/api/sqs/…` and, through it, [CLI](cli.md)
commands. These routes read and write the same state as the AWS-protocol stubs — a message sent with
the SDK is returned by `clm sqs receive-message`, and vice versa.

| Command                   | Route                          | Parameters          | Description        |
| ------------------------- | ------------------------------ | ------------------- | ------------------ |
| `clm sqs list-queues`     | `GET /api/sqs/list-queues`     | —                   | List queues        |
| `clm sqs send-message`    | `POST /api/sqs/send-message`   | `--queue`, `--body` | Send a message     |
| `clm sqs receive-message` | `GET /api/sqs/receive-message` | `--queue`           | Receive messages   |
| `clm sqs purge-queue`     | `POST /api/sqs/purge-queue`    | `--queue`           | Purge all messages |

## Supported operations

State-backed operations return live data from the shared state store:

| Operation            | Behavior                                                             |
| -------------------- | ------------------------------------------------------------------- |
| `CreateQueue`        | Records the queue; returns its URL                                  |
| `GetQueueUrl`        | Returns the URL for a queue name                                    |
| `SendMessage`        | Stores the message body; returns `MessageId` and `MD5OfMessageBody` |
| `ReceiveMessage`     | Returns stored messages (honors `MaxNumberOfMessages`)              |
| `DeleteMessage`      | Removes a message by receipt handle                                 |
| `DeleteQueue`        | Removes the queue and its messages                                  |
| `ListQueues`         | Returns the URLs of all created queues                              |
| `GetQueueAttributes` | Returns attributes, including the live `ApproximateNumberOfMessages`|

The remaining operations are registered and return well-formed but stateless placeholder responses;
they do not read or mutate state: `AddPermission`, `CancelMessageMoveTask`, `ChangeMessageVisibility`,
`ChangeMessageVisibilityBatch`, `DeleteMessageBatch`, `ListDeadLetterSourceQueues`,
`ListMessageMoveTasks`, `ListQueueTags`, `PurgeQueue`, `RemovePermission`, `SendMessageBatch`,
`SetQueueAttributes`, `StartMessageMoveTask`, `TagQueue`, `UntagQueue`.

To purge a queue against state, use the friendly `clm sqs purge-queue` command (see
[REST and CLI access](#rest-and-cli-access)); the AWS-protocol `PurgeQueue` operation is a placeholder
and does not clear messages.

## Limitations

- Visibility timeout is not enforced — a received message stays visible until it is explicitly
  deleted (no in-flight tracking or `ApproximateNumberOfMessagesNotVisible` accounting).
- FIFO queues are treated like standard queues — the `.fifo` name and `FifoQueue` attribute are
  accepted but no deduplication or ordering is applied.
- Dead-letter queues and message-move tasks are placeholders — no redrive is performed.
- Batch operations (`SendMessageBatch`, `DeleteMessageBatch`, `ChangeMessageVisibilityBatch`) are
  placeholders and do not mutate state.
- Queue permissions and tagging are placeholders.
- `SetQueueAttributes` is a placeholder; queue attributes returned by `GetQueueAttributes` are fixed
  defaults (plus the live message count) and cannot be changed.
- Message attributes, system attributes, and delay seconds are not stored or returned.
- The SQS Query API (queue URLs as HTTP endpoints with `?Action=…`) is not supported; CloudStub
  serves SQS over the JSON / `X-Amz-Target` protocol only.
```

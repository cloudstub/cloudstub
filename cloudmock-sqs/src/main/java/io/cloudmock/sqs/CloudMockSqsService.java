package io.cloudmock.sqs;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

/**
 * CloudMock service module for Amazon SQS.
 *
 * <p>Registers stateless JSON stubs for the core SQS operations. AWS SDK v2 (≥2.20)
 * uses the JSON/X-Amz-Target protocol for SQS — requests carry an {@code X-Amz-Target}
 * header (e.g. {@code AmazonSQS.CreateQueue}) and a JSON body. Responses are well-formed
 * JSON that the SDK can parse without error.
 *
 * <p>Queue state is not simulated — {@code ReceiveMessage} always returns a synthetic
 * message regardless of prior {@code SendMessage} calls. This is Stage 1 contract mocking.
 *
 * <p>Discovered automatically via {@code ServiceLoader} from
 * {@code META-INF/services/io.cloudmock.core.spi.CloudMockService}.
 */
public class CloudMockSqsService implements CloudMockService {

    private static final String SERVICE_ID = "sqs";
    private static final String PREFIX     = "AmazonSQS.";

    // {{randomValue type='UUID'}} generates a fresh UUID per request.
    // {{jsonPath request.body '$.QueueName'}} echoes the queue name from the JSON body.

    private static final String CREATE_QUEUE =
            """
            {"QueueUrl":"http://localhost/000000000000/{{jsonPath request.body '$.QueueName'}}"}""";

    private static final String GET_QUEUE_URL =
            """
            {"QueueUrl":"http://localhost/000000000000/{{jsonPath request.body '$.QueueName'}}"}""";

    // {{md5 ...}} is a custom CloudMock Handlebars helper that computes MD5 at request time,
    // so the checksum always matches whatever body is actually sent or returned.
    private static final String SEND_MESSAGE =
            """
            {"MessageId":"{{randomValue type='UUID'}}","MD5OfMessageBody":"{{md5 (jsonPath request.body '$.MessageBody')}}"}""";

    private static final String RECEIVE_MESSAGE =
            """
            {"Messages":[{"MessageId":"{{randomValue type='UUID'}}","ReceiptHandle":"{{randomValue type='UUID'}}","Body":"cloudmock-synthetic-message","MD5OfBody":"{{md5 'cloudmock-synthetic-message'}}"}]}""";

    private static final String DELETE_MESSAGE = "{}";

    private static final String DELETE_QUEUE = "{}";

    private static final String LIST_QUEUES =
            """
            {"QueueUrls":["http://localhost/000000000000/queue"]}""";

    private static final String GET_QUEUE_ATTRIBUTES =
            """
            {"Attributes":{"VisibilityTimeout":"30","ApproximateNumberOfMessages":"0","ApproximateNumberOfMessagesNotVisible":"0","MaximumMessageSize":"262144","MessageRetentionPeriod":"345600","ReceiveMessageWaitTimeSeconds":"0"}}""";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void register(CloudMockContext context) {
        StubRegistrar registrar = context.registrar();
        registrar.registerJsonTargetStub(PREFIX + "CreateQueue",        CREATE_QUEUE);
        registrar.registerJsonTargetStub(PREFIX + "GetQueueUrl",        GET_QUEUE_URL);
        registrar.registerJsonTargetStub(PREFIX + "SendMessage",        SEND_MESSAGE);
        registrar.registerJsonTargetStub(PREFIX + "ReceiveMessage",     RECEIVE_MESSAGE);
        registrar.registerJsonTargetStub(PREFIX + "DeleteMessage",      DELETE_MESSAGE);
        registrar.registerJsonTargetStub(PREFIX + "DeleteQueue",        DELETE_QUEUE);
        registrar.registerJsonTargetStub(PREFIX + "ListQueues",         LIST_QUEUES);
        registrar.registerJsonTargetStub(PREFIX + "GetQueueAttributes", GET_QUEUE_ATTRIBUTES);
    }
}

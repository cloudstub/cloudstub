package io.cloudmock.sqs;

import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubRegistrar;

/**
 * CloudMock service module for Amazon SQS.
 *
 * <p>Registers stateless XML/Form URL stubs for the core SQS operations. Responses are
 * well-formed enough for the AWS SDK v2 {@code SqsClient} to parse without error. Queue
 * state is not simulated — {@code ReceiveMessage} always returns a synthetic message
 * regardless of prior {@code SendMessage} calls.
 *
 * <p>This module is the reference implementation for the XML/Form URL routing protocol.
 * All Handlebars templates here serve as the canonical pattern for future Form URL modules.
 *
 * <p>Discovered automatically via {@code ServiceLoader} from
 * {@code META-INF/services/io.cloudmock.core.spi.CloudMockService}.
 */
public class CloudMockSqsService implements CloudMockService {

    private static final String NS = "http://queue.amazonaws.com/doc/2012-11-05/";

    // --- Response templates -------------------------------------------------
    // {{randomValue type='UUID'}} is evaluated by WireMock's Handlebars engine
    // at request time, so every response gets a fresh correlation identifier.
    // Queue URLs are synthetic placeholders; the SDK accepts any valid URL.

    private static final String CREATE_QUEUE = String.format("""
            <?xml version="1.0"?>
            <CreateQueueResponse xmlns="%s">
              <CreateQueueResult>
                <QueueUrl>http://localhost/000000000000/queue</QueueUrl>
              </CreateQueueResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </CreateQueueResponse>""", NS);

    private static final String GET_QUEUE_URL = String.format("""
            <?xml version="1.0"?>
            <GetQueueUrlResponse xmlns="%s">
              <GetQueueUrlResult>
                <QueueUrl>http://localhost/000000000000/queue</QueueUrl>
              </GetQueueUrlResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </GetQueueUrlResponse>""", NS);

    // MD5OfMessageBody is a fixed valid MD5; the SDK does not verify it against the body.
    private static final String SEND_MESSAGE = String.format("""
            <?xml version="1.0"?>
            <SendMessageResponse xmlns="%s">
              <SendMessageResult>
                <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                <MessageId>{{randomValue type='UUID'}}</MessageId>
              </SendMessageResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </SendMessageResponse>""", NS);

    private static final String RECEIVE_MESSAGE = String.format("""
            <?xml version="1.0"?>
            <ReceiveMessageResponse xmlns="%s">
              <ReceiveMessageResult>
                <Message>
                  <MessageId>{{randomValue type='UUID'}}</MessageId>
                  <ReceiptHandle>{{randomValue type='UUID'}}</ReceiptHandle>
                  <MD5OfBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfBody>
                  <Body>cloudmock-synthetic-message</Body>
                </Message>
              </ReceiveMessageResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </ReceiveMessageResponse>""", NS);

    private static final String DELETE_MESSAGE = String.format("""
            <?xml version="1.0"?>
            <DeleteMessageResponse xmlns="%s">
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </DeleteMessageResponse>""", NS);

    private static final String DELETE_QUEUE = String.format("""
            <?xml version="1.0"?>
            <DeleteQueueResponse xmlns="%s">
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </DeleteQueueResponse>""", NS);

    private static final String GET_QUEUE_ATTRIBUTES = String.format("""
            <?xml version="1.0"?>
            <GetQueueAttributesResponse xmlns="%s">
              <GetQueueAttributesResult>
                <Attribute><Name>VisibilityTimeout</Name><Value>30</Value></Attribute>
                <Attribute><Name>ApproximateNumberOfMessages</Name><Value>0</Value></Attribute>
                <Attribute><Name>ApproximateNumberOfMessagesNotVisible</Name><Value>0</Value></Attribute>
                <Attribute><Name>MaximumMessageSize</Name><Value>262144</Value></Attribute>
                <Attribute><Name>MessageRetentionPeriod</Name><Value>345600</Value></Attribute>
                <Attribute><Name>ReceiveMessageWaitTimeSeconds</Name><Value>0</Value></Attribute>
              </GetQueueAttributesResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </GetQueueAttributesResponse>""", NS);

    private static final String LIST_QUEUES = String.format("""
            <?xml version="1.0"?>
            <ListQueuesResponse xmlns="%s">
              <ListQueuesResult>
                <QueueUrl>http://localhost/000000000000/queue</QueueUrl>
              </ListQueuesResult>
              <ResponseMetadata>
                <RequestId>{{randomValue type='UUID'}}</RequestId>
              </ResponseMetadata>
            </ListQueuesResponse>""", NS);

    // ------------------------------------------------------------------------

    @Override
    public String serviceId() {
        return "sqs";
    }

    @Override
    public void register(StubRegistrar registrar) {
        registrar.registerXmlFormStub("CreateQueue",    CREATE_QUEUE);
        registrar.registerXmlFormStub("GetQueueUrl",    GET_QUEUE_URL);
        registrar.registerXmlFormStub("SendMessage",    SEND_MESSAGE);
        registrar.registerXmlFormStub("ReceiveMessage", RECEIVE_MESSAGE);
        registrar.registerXmlFormStub("DeleteMessage",  DELETE_MESSAGE);
        registrar.registerXmlFormStub("DeleteQueue",    DELETE_QUEUE);
        registrar.registerXmlFormStub("GetQueueAttributes", GET_QUEUE_ATTRIBUTES);
        registrar.registerXmlFormStub("ListQueues",          LIST_QUEUES);
    }
}

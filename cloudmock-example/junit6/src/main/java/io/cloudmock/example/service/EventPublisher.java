package io.cloudmock.example.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

/**
 * Publishes events to an SQS queue, creating it on first use.
 */
@Service
public class EventPublisher {

    private final SqsClient sqs;
    private final String queueName;
    private String queueUrl;

    public EventPublisher(SqsClient sqs) {
        this.sqs = sqs;
        this.queueName = "events";
    }

    /** Creates the queue if this is the first call, then sends {@code payload}. Returns the message ID. */
    public String publish(String payload) {
        if (queueUrl == null) {
            queueUrl = sqs.createQueue(b -> b.queueName(queueName)).queueUrl();
        }
        return sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody(payload)).messageId();
    }

    /** Polls for up to 10 pending messages and returns their bodies. */
    public List<String> poll() {
        if (queueUrl == null) {
            return List.of();
        }
        return sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(10))
                  .messages().stream()
                  .map(Message::body)
                  .toList();
    }
}

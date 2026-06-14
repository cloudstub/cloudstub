package io.cloudstub.example.service;

import java.util.List;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/** Publishes events to an SQS queue, creating it on first use. */
@Service
public class EventPublisher {

    private final SqsClient sqs;
    private final String queueName;
    private String queueUrl;

    public EventPublisher(SqsClient sqs) {
        this.sqs = sqs;
        this.queueName = "events";
    }

    /**
     * Creates the queue if this is the first call, then sends {@code payload}. Returns the message
     * ID.
     */
    public String publish(String payload) {
        if (queueUrl == null) {
            queueUrl = sqs.createQueue(b -> b.queueName(queueName)).queueUrl();
        }
        return sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody(payload)).messageId();
    }

    /**
     * Receives up to 10 pending messages and returns their bodies without removing them. A peek:
     * the messages stay in the queue and a later call returns them again.
     */
    public List<String> poll() {
        if (queueUrl == null) {
            return List.of();
        }
        return sqs
                .receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(10))
                .messages()
                .stream()
                .map(Message::body)
                .toList();
    }

    /**
     * Receives up to 10 pending messages, deletes each from the queue, and returns their bodies.
     * The full SQS consume cycle (receive then delete), so consumed messages do not come back.
     */
    public List<String> consume() {
        if (queueUrl == null) {
            return List.of();
        }

        // Do something with the messages, then delete them from the queue.
        List<Message> messages =
                sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(10)).messages();
        for (Message message : messages) {
            sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        }
        return messages.stream().map(Message::body).toList();
    }
}

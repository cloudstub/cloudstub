package io.cloudstub.example.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;

/** Publishes messages to an SNS topic, creating it on first use. */
@Service
public class TopicPublisher {

    private final SnsClient sns;
    private final String topicName;
    private String topicArn;

    public TopicPublisher(SnsClient sns) {
        this.sns = sns;
        this.topicName = "notifications";
    }

    /**
     * Creates the topic if this is the first call, then publishes {@code message}. Returns the
     * message ID.
     */
    public String publish(String message) {
        return sns.publish(b -> b.topicArn(topicArn()).message(message)).messageId();
    }

    /**
     * Subscribes {@code endpoint} to the topic for the given protocol (e.g. {@code sqs}, {@code
     * https}), creating the topic on first use. Returns the subscription ARN.
     */
    public String subscribe(String protocol, String endpoint) {
        return sns.subscribe(b -> b.topicArn(topicArn()).protocol(protocol).endpoint(endpoint))
                .subscriptionArn();
    }

    private String topicArn() {
        if (topicArn == null) {
            topicArn = sns.createTopic(b -> b.name(topicName)).topicArn();
        }
        return topicArn;
    }
}

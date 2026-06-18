package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.TopicPublisher;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class TopicPublisherIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Autowired TopicPublisher publisher;

    @Test
    void publishCreatesTopicOnFirstCallAndReturnsMessageId() {
        String messageId = publisher.publish("order-placed");
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }

    @Test
    void subscribeReturnsSubscriptionArn() {
        String subscriptionArn =
                publisher.subscribe(
                        "sqs", "arn:aws:sqs:us-east-1:000000000000:notifications-queue");
        assertNotNull(subscriptionArn);
        assertFalse(subscriptionArn.isBlank());
    }
}

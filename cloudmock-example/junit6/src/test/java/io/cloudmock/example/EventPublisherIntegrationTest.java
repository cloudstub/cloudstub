package io.cloudmock.example;

import io.cloudmock.example.service.EventPublisher;
import io.cloudmock.junit.CloudMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class EventPublisherIntegrationTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension();

    @Autowired EventPublisher publisher;

    @Test
    void publishCreatesQueueOnFirstCallAndReturnsMessageId() {
        String messageId = publisher.publish("order-placed");
        assertNotNull(messageId);
        assertFalse(messageId.isBlank());
    }

    @Test
    void pollReturnsMessagesAfterPublish() {
        publisher.publish("order-shipped");
        assertFalse(publisher.poll().isEmpty());
    }
}

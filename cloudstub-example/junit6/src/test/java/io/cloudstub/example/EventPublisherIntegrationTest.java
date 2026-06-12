package io.cloudstub.example;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.example.service.EventPublisher;
import io.cloudstub.junit.CloudStubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class EventPublisherIntegrationTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

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

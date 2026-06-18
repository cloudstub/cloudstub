package io.cloudstub.example.runner;

import io.cloudstub.example.service.TopicPublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises {@link TopicPublisher} against a running SNS endpoint and logs the results. Active only
 * under the {@code sns} Spring profile, so it never runs during the test suite.
 *
 * <p>Activate the {@code local} profile alongside {@code sns} (e.g. {@code
 * --spring.profiles.active=local,sns}) so the clients point at a standalone CloudStub server and
 * this runner verifies the SNS module end to end.
 */
@Component
@Profile("sns")
public class SnsDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SnsDemoRunner.class);

    private final TopicPublisher publisher;

    public SnsDemoRunner(TopicPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void run(String... args) {
        String subscriptionArn =
                publisher.subscribe(
                        "sqs", "arn:aws:sqs:us-east-1:000000000000:notifications-queue");
        log.info("Subscribed endpoint -> subscriptionArn={}", subscriptionArn);

        List<String> messages = List.of("order-placed", "order-paid", "order-shipped");
        log.info("Publishing {} notifications to SNS...", messages.size());
        for (String message : messages) {
            String messageId = publisher.publish(message);
            log.info("  published '{}' -> messageId={}", message, messageId);
        }

        log.info("SNS publish OK: subscribed and published {} notifications", messages.size());
    }
}

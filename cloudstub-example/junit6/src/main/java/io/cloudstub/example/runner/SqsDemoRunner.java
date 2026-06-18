package io.cloudstub.example.runner;

import io.cloudstub.example.service.QueuePublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises {@link QueuePublisher} against a running SQS endpoint and logs the results. Active only
 * under the {@code sqs} Spring profile, so it never runs during the test suite.
 *
 * <p>Activate the {@code local} profile alongside {@code sqs} (e.g. {@code
 * --spring.profiles.active=local,sqs}) so the clients point at a standalone CloudStub server and
 * this runner verifies the SQS module end to end.
 */
@Component
@Profile("sqs")
public class SqsDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SqsDemoRunner.class);

    private final QueuePublisher publisher;

    public SqsDemoRunner(QueuePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void run(String... args) {
        List<String> payloads = List.of("order-placed", "order-paid", "order-shipped");

        log.info("Publishing {} messages to SQS...", payloads.size());
        for (String payload : payloads) {
            String messageId = publisher.publish(payload);
            log.info("  sent '{}' -> messageId={}", payload, messageId);
        }

        // poll() is a non-destructive peek: messages stay in the queue.
        List<String> peeked = publisher.poll();
        log.info("Peeked {} messages (still in the queue):", peeked.size());
        peeked.forEach(body -> log.info("  peeked '{}'", body));

        // consume() receives and deletes: the full SQS consume cycle.
        List<String> consumed = publisher.consume();
        log.info("Consumed {} messages (received and deleted):", consumed.size());
        consumed.forEach(body -> log.info("  consumed '{}'", body));

        // After consuming, the queue is empty again.
        int remaining = publisher.poll().size();
        if (consumed.size() == payloads.size() && remaining == 0) {
            log.info(
                    "SQS round-trip OK: published, peeked, and consumed {} messages; queue now"
                            + " empty",
                    payloads.size());
        } else {
            log.warn(
                    "SQS round-trip mismatch: published {}, consumed {}, {} still in queue",
                    payloads.size(),
                    consumed.size(),
                    remaining);
        }
    }
}

package io.cloudstub.example.runner;

import io.cloudstub.example.service.ItemRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises {@link ItemRepository} against a running DynamoDB endpoint and logs the results. Active
 * only under the {@code dynamodb} Spring profile, so it never runs during the test suite.
 *
 * <p>Activate the {@code local} profile alongside {@code dynamodb} (e.g. {@code
 * --spring.profiles.active=local,dynamodb}) so the client points at a standalone CloudStub server
 * and this runner verifies the DynamoDB module end to end.
 */
@Component
@Profile("dynamodb")
public class DynamoDbDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbDemoRunner.class);

    private final ItemRepository repository;

    public DynamoDbDemoRunner(ItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        repository.save("order-1", "placed");
        log.info("Saved item order-1=placed");

        Optional<String> found = repository.find("order-1");
        log.info("Read item order-1 -> {}", found.orElse("<absent>"));

        Optional<String> missing = repository.find("order-2");
        if (found.filter("placed"::equals).isPresent() && missing.isEmpty()) {
            log.info("DynamoDB round-trip OK: put item is read back; absent key returns nothing");
        } else {
            log.warn("DynamoDB round-trip mismatch: order-1={}, order-2={}", found, missing);
        }
    }
}

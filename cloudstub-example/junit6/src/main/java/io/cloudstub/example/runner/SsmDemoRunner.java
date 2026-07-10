package io.cloudstub.example.runner;

import io.cloudstub.example.service.ConfigStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises {@link ConfigStore} against a running SSM endpoint and logs the results. Active only
 * under the {@code ssm} Spring profile, so it never runs during the test suite.
 *
 * <p>Activate the {@code local} profile alongside {@code ssm} (e.g. {@code
 * --spring.profiles.active=local,ssm}) so the client points at a standalone CloudStub server and
 * this runner verifies the SSM module end to end.
 */
@Component
@Profile("ssm")
public class SsmDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SsmDemoRunner.class);

    private final ConfigStore configStore;

    public SsmDemoRunner(ConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public void run(String... args) {
        configStore.put("/demo/feature-flag", "enabled");
        log.info("Stored /demo/feature-flag=enabled");

        Optional<String> found = configStore.get("/demo/feature-flag");
        log.info("Read /demo/feature-flag -> {}", found.orElse("<absent>"));

        String missing = configStore.getOrDefault("/demo/absent", "<default>");
        if (found.filter("enabled"::equals).isPresent() && "<default>".equals(missing)) {
            log.info("SSM round-trip OK: put value is read back; absent key returns the default");
        } else {
            log.warn("SSM round-trip mismatch: flag={}, absent={}", found, missing);
        }
    }
}

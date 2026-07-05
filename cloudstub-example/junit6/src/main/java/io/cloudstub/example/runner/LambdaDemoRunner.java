package io.cloudstub.example.runner;

import io.cloudstub.example.service.FunctionInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises {@link FunctionInvoker} against a running Lambda endpoint and logs the result. Active
 * only under the {@code lambda} Spring profile, so it never runs during the test suite.
 *
 * <p>Activate the {@code local} profile alongside {@code lambda} (e.g. {@code
 * --spring.profiles.active=local,lambda}) so the client points at a standalone CloudStub server and
 * this runner verifies the Lambda module end to end.
 */
@Component
@Profile("lambda")
public class LambdaDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LambdaDemoRunner.class);

    private final FunctionInvoker invoker;

    public LambdaDemoRunner(FunctionInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public void run(String... args) {
        String request = "{\"order\":42}";
        String response = invoker.invoke(request);
        log.info("Invoked processor with {} -> {}", request, response);

        if (request.equals(response)) {
            log.info("Lambda round-trip OK: the function was deployed and invoked");
        } else {
            log.warn("Lambda round-trip mismatch: sent {}, got {}", request, response);
        }
    }
}

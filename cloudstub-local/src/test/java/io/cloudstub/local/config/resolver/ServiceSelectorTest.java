package io.cloudstub.local.config.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceSelectorTest {

    @Test
    void returnsNullWhenNoSelectionRequested() {
        assertNull(ServiceSelector.resolve(new String[] {"--port=4566"}));
    }

    @Test
    void parsesEqualsFormFlag() {
        assertEquals(
                Set.of("sqs", "secretsmanager"),
                ServiceSelector.resolve(new String[] {"--services=sqs,secretsmanager"}));
    }

    @Test
    void parsesSpaceSeparatedFlag() {
        assertEquals(
                Set.of("sqs", "sns"),
                ServiceSelector.resolve(new String[] {"--services", "sqs,sns"}));
    }

    @Test
    void trimsWhitespaceAndDropsBlankEntries() {
        assertEquals(
                Set.of("sqs", "s3"),
                ServiceSelector.resolve(new String[] {"--services= sqs , , s3 "}));
    }

    @Test
    void blankValueIsTreatedAsNoSelection() {
        assertNull(ServiceSelector.resolve(new String[] {"--services="}));
    }
}

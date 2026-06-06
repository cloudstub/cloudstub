package io.cloudmock.standalone;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModuleSelectorTest {

    @Test
    void returnsNullWhenNoFilterRequested() {
        assertNull(ModuleSelector.resolve(new String[] {"--port=4566"}));
    }

    @Test
    void parsesEqualsFormFlag() {
        assertEquals(Set.of("sqs", "secretsmanager"),
                ModuleSelector.resolve(new String[] {"--modules=sqs,secretsmanager"}));
    }

    @Test
    void parsesSpaceSeparatedFlag() {
        assertEquals(Set.of("sqs", "sns"),
                ModuleSelector.resolve(new String[] {"--modules", "sqs,sns"}));
    }

    @Test
    void trimsWhitespaceAndDropsBlankEntries() {
        assertEquals(Set.of("sqs", "s3"),
                ModuleSelector.resolve(new String[] {"--modules= sqs , , s3 "}));
    }

    @Test
    void blankValueIsTreatedAsNoFilter() {
        assertNull(ModuleSelector.resolve(new String[] {"--modules="}));
    }
}

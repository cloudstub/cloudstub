package io.cloudstub.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StubTemplatesTest {

    @Test
    void loadsTemplateContents() {
        assertEquals(
                "<SampleResponse>ok</SampleResponse>", StubTemplates.load(getClass(), "Sample"));
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        String loaded = StubTemplates.load(getClass(), "Sample");
        assertEquals(loaded.trim(), loaded);
        assertTrue(loaded.startsWith("<SampleResponse>"));
        assertTrue(loaded.endsWith("</SampleResponse>"));
    }

    @Test
    void missingTemplateFailsWithResourcePath() {
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> StubTemplates.load(getClass(), "DoesNotExist"));
        assertEquals("Template not found: /templates/DoesNotExist.hbs", ex.getMessage());
    }
}

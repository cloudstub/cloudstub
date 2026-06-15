package io.cloudstub.local.config.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PortResolverTest {

    @Test
    void defaultsTo4566() {
        assertEquals(PortResolver.DEFAULT_PORT, PortResolver.resolve(new String[0]));
    }

    @Test
    void parsesEqualsFormFlag() {
        assertEquals(9000, PortResolver.resolve(new String[] {"--port=9000"}));
    }

    @Test
    void parsesSpaceSeparatedFlag() {
        assertEquals(9001, PortResolver.resolve(new String[] {"--port", "9001"}));
    }

    @Test
    void nonNumericFlagFallsThroughToDefault() {
        assertEquals(PortResolver.DEFAULT_PORT, PortResolver.resolve(new String[] {"--port=abc"}));
    }
}

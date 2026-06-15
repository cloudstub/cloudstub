package io.cloudstub.local.config.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiPortResolverTest {

    @Test
    void defaultsTo4567() {
        assertEquals(4567, ApiPortResolver.resolve(new String[0]));
    }

    @Test
    void parsesLongFlagWithEquals() {
        assertEquals(9000, ApiPortResolver.resolve(new String[] {"--api-port=9000"}));
    }

    @Test
    void parsesLongFlagWithSpace() {
        assertEquals(9001, ApiPortResolver.resolve(new String[] {"--api-port", "9001"}));
    }
}

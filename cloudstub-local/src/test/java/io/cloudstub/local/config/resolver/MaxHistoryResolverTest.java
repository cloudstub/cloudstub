package io.cloudstub.local.config.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cloudstub.core.CloudStub;
import org.junit.jupiter.api.Test;

class MaxHistoryResolverTest {

    @Test
    void defaultsToCoreDefault() {
        assertEquals(
                CloudStub.DEFAULT_MAX_REQUEST_HISTORY, MaxHistoryResolver.resolve(new String[0]));
    }

    @Test
    void parsesLongFlagWithEquals() {
        assertEquals(50, MaxHistoryResolver.resolve(new String[] {"--max-history=50"}));
    }

    @Test
    void parsesLongFlagWithSpace() {
        assertEquals(50, MaxHistoryResolver.resolve(new String[] {"--max-history", "50"}));
    }

    @Test
    void unlimitedKeywordResolvesToZero() {
        assertEquals(0, MaxHistoryResolver.resolve(new String[] {"--max-history=unlimited"}));
    }

    @Test
    void noneKeywordResolvesToZero() {
        assertEquals(0, MaxHistoryResolver.resolve(new String[] {"--max-history=none"}));
    }

    @Test
    void nonNumericFlagFallsThroughToDefault() {
        assertEquals(
                CloudStub.DEFAULT_MAX_REQUEST_HISTORY,
                MaxHistoryResolver.resolve(new String[] {"--max-history=abc"}));
    }
}

package io.cloudstub.local.config.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutoDownloadResolverTest {

    @Test
    void enabledByDefault() {
        assertTrue(AutoDownloadResolver.isEnabled(new String[] {}));
        assertTrue(AutoDownloadResolver.isEnabled(new String[] {"--services=sqs"}));
    }

    @Test
    void noDownloadFlagDisables() {
        assertFalse(AutoDownloadResolver.isEnabled(new String[] {"--no-download"}));
        assertFalse(
                AutoDownloadResolver.isEnabled(new String[] {"--services=sqs", "--no-download"}));
    }
}

package io.cloudstub.core.download;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CoreVersionTest {

    @Test
    void reportsStampedVersion() {
        String version = CoreVersion.current();
        assertNotNull(version);
        assertFalse(version.isBlank());
        // The build stamps the project version; during development it is a SNAPSHOT.
        assertFalse(version.contains(" "));
    }
}

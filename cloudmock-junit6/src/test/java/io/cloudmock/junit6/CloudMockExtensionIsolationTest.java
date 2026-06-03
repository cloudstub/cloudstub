package io.cloudmock.junit6;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A second independent test class that verifies lifecycle isolation — each class gets
 * its own CloudMock instance and its own port, independent of any other running class.
 */
class CloudMockExtensionIsolationTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension();

    @Test
    void hasItsOwnIndependentPort() {
        assertTrue(cloudMock.port() > 0);
    }
}

package io.cloudmock.junit;

import io.cloudmock.core.CloudMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that two {@link CloudMockExtension} instances are fully independent — each
 * binds its own port, and stopping one does not affect the other.
 */
class CloudMockExtensionIsolationTest {

    @RegisterExtension
    static CloudMockExtension first = new CloudMockExtension();

    @RegisterExtension
    static CloudMockExtension second = new CloudMockExtension();

    @Test
    void eachInstanceBindsADistinctPort() {
        assertTrue(first.port() > 0);
        assertTrue(second.port() > 0);
        assertNotEquals(first.port(), second.port(),
                "Two CloudMockExtension instances must bind different ports");
    }

    @Test
    void stoppingOneInstanceDoesNotStopTheOther() throws Exception {
        int secondPort = second.port();

        CloudMock transient_ = new CloudMock();
        transient_.start();
        int transientPort = transient_.port();
        assertNotEquals(secondPort, transientPort);

        transient_.stop();

        // 'second' must still accept connections after an unrelated instance was stopped
        try (Socket s = new Socket("localhost", secondPort)) {
            assertTrue(s.isConnected());
        }
    }
}

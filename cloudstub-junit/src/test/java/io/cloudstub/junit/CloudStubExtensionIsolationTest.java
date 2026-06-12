package io.cloudstub.junit;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.CloudStub;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that two {@link CloudStubExtension} instances are fully independent — each binds its own
 * port, and stopping one does not affect the other.
 */
class CloudStubExtensionIsolationTest {

    @RegisterExtension static CloudStubExtension first = new CloudStubExtension();

    @RegisterExtension static CloudStubExtension second = new CloudStubExtension();

    @Test
    void eachInstanceBindsADistinctPort() {
        assertTrue(first.port() > 0);
        assertTrue(second.port() > 0);
        assertNotEquals(
                first.port(),
                second.port(),
                "Two CloudStubExtension instances must bind different ports");
    }

    @Test
    void stoppingOneInstanceDoesNotStopTheOther() throws Exception {
        int secondPort = second.port();

        CloudStub transient_ = new CloudStub();
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

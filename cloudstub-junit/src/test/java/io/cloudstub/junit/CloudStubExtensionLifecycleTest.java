package io.cloudstub.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that {@link CloudStubExtension} starts and stops CloudStub around the test class and
 * that the port is accessible via {@code @RegisterExtension}.
 */
class CloudStubExtensionLifecycleTest {

    @RegisterExtension static CloudStubExtension cloudMock = new CloudStubExtension();

    @Test
    void portIsPositiveAfterStart() {
        assertTrue(cloudMock.port() > 0);
    }

    @Test
    void serverAcceptsConnectionsAfterStart() throws Exception {
        try (Socket socket = new Socket("localhost", cloudMock.port())) {
            assertTrue(socket.isConnected());
        }
    }
}

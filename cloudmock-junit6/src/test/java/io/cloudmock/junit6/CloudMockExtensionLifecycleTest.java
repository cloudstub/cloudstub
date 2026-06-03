package io.cloudmock.junit6;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link CloudMockExtension} starts and stops CloudMock around the test class
 * and that the port is accessible via {@code @RegisterExtension}.
 */
class CloudMockExtensionLifecycleTest {

    @RegisterExtension
    static CloudMockExtension cloudMock = new CloudMockExtension();

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

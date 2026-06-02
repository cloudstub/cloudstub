package io.cloudmock.core;

import io.cloudmock.core.exception.CloudMockAlreadyStartedException;
import io.cloudmock.core.exception.CloudMockNotStartedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudMockTest {

    private final CloudMock cloudMock = new CloudMock();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    @Test
    void startSetsEndpointProperty() {
        cloudMock.start();
        String url = System.getProperty("aws.endpoint-url");
        assertNotNull(url);
        assertTrue(url.startsWith("http://localhost:"));
    }

    @Test
    void stopRemovesEndpointProperty() {
        cloudMock.start();
        cloudMock.stop();
        assertNull(System.getProperty("aws.endpoint-url"));
    }

    @Test
    void portIsReachableAfterStart() throws Exception {
        cloudMock.start();
        int port = cloudMock.port();
        assertTrue(port > 0);
        // Verify the server actually accepts connections.
        try (var socket = new java.net.Socket("localhost", port)) {
            assertTrue(socket.isConnected());
        }
    }

    @Test
    void doubleStartThrowsCloudMockAlreadyStartedException() {
        cloudMock.start();
        assertThrows(CloudMockAlreadyStartedException.class, cloudMock::start);
    }

    @Test
    void stopBeforeStartIsNoOp() {
        assertDoesNotThrow(cloudMock::stop);
    }

    @Test
    void closeStopsTheServer() {
        cloudMock.start();
        int port = cloudMock.port();
        cloudMock.close();
        assertNull(System.getProperty("aws.endpoint-url"));
        assertThrows(CloudMockNotStartedException.class, cloudMock::port);
    }
}

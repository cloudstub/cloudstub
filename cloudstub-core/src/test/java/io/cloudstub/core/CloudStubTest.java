package io.cloudstub.core;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.exception.CloudStubAlreadyStartedException;
import io.cloudstub.core.exception.CloudStubNotStartedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CloudStubTest {

    private final CloudStub cloudMock = new CloudStub();

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
    void doubleStartThrowsCloudStubAlreadyStartedException() {
        cloudMock.start();
        assertThrows(CloudStubAlreadyStartedException.class, cloudMock::start);
    }

    @Test
    void withPortBindsToTheSpecifiedPort() throws Exception {
        int port;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        cloudMock.withPort(port).start();
        assertEquals(port, cloudMock.port());
    }

    @Test
    void withPortAfterStartThrowsCloudStubAlreadyStartedException() {
        cloudMock.start();
        assertThrows(CloudStubAlreadyStartedException.class, () -> cloudMock.withPort(9999));
    }

    @Test
    void withEnabledServicesAfterStartThrowsCloudStubAlreadyStartedException() {
        cloudMock.start();
        assertThrows(
                CloudStubAlreadyStartedException.class,
                () -> cloudMock.withEnabledServices(java.util.Set.of("sqs")));
    }

    @Test
    void stopBeforeStartIsNoOp() {
        assertDoesNotThrow(cloudMock::stop);
    }

    @Test
    void closeStopsTheServer() {
        cloudMock.start();
        cloudMock.close();
        assertNull(System.getProperty("aws.endpoint-url"));
        assertThrows(CloudStubNotStartedException.class, cloudMock::port);
    }
}

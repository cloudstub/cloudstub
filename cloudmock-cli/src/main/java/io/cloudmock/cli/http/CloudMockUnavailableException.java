package io.cloudmock.cli.http;

/** Thrown when the CloudMock REST API cannot be reached (server down, wrong port, network error). */
public class CloudMockUnavailableException extends Exception {

    public CloudMockUnavailableException(String baseUrl) {
        super("CloudMock is not reachable at " + baseUrl);
    }

    public CloudMockUnavailableException(String baseUrl, Throwable cause) {
        super("CloudMock is not reachable at " + baseUrl + ": " + cause.getMessage(), cause);
    }
}

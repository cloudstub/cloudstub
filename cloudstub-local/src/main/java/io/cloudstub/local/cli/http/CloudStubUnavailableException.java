package io.cloudstub.local.cli.http;

/**
 * Thrown when the CloudStub REST API cannot be reached (server down, wrong port, network error).
 */
public class CloudStubUnavailableException extends Exception {

    public CloudStubUnavailableException(String baseUrl) {
        super("CloudStub is not reachable at " + baseUrl);
    }

    public CloudStubUnavailableException(String baseUrl, Throwable cause) {
        super("CloudStub is not reachable at " + baseUrl + ": " + cause.getMessage(), cause);
    }
}

package io.cloudstub.core.spi.restapi;

/** Handler for a single API route registered by a service module. */
@FunctionalInterface
public interface ApiHandler {
    ApiResponse handle(ApiRequest request);
}

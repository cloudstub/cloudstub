package io.cloudstub.core.spi.restapi;

import java.util.Map;

/** Response produced by a module API handler. Body is serialised to JSON by the API server. */
public record ApiResponse(int statusCode, Map<String, Object> body) {}

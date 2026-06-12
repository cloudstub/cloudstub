package io.cloudstub.core.spi.restapi;

import java.util.Map;

/** Inbound API HTTP request, stripped of HTTP-transport details. */
public record ApiRequest(String method, String path, Map<String, String> queryParams) {}

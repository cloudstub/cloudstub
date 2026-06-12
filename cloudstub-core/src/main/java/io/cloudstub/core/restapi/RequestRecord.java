package io.cloudstub.core.restapi;

/** Immutable snapshot of a single request served by the mock engine. */
public record RequestRecord(
        String timestamp,
        String method,
        String url,
        String serviceId,
        String operation,
        int statusCode,
        boolean matched) {}

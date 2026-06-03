package io.cloudmock.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records which stubs each service module registered, so {@link FaultEngine} can generate
 * matching fault stubs for any service without coupling to its implementation.
 */
class ServiceRegistry {

    private final Map<String, List<StubRecord>> byService = new HashMap<>();

    public void record(String serviceId, StubRecord record) {
        byService.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(record);
    }

    public List<StubRecord> getStubs(String serviceId) {
        return Collections.unmodifiableList(byService.getOrDefault(serviceId, List.of()));
    }
}

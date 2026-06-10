package io.cloudmock.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records which stubs each service module registered, so {@link CloudMockResponseTransformer} can
 * look up a matched stub's protocol when decorating its response with a fault, without coupling to
 * the module's implementation.
 */
public class ServiceRegistry {

    private final Map<String, List<StubRecord>> byService = new HashMap<>();

    void record(String serviceId, StubRecord record) {
        byService.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(record);
    }

    List<StubRecord> getStubs(String serviceId) {
        return Collections.unmodifiableList(byService.getOrDefault(serviceId, List.of()));
    }

    /** The recorded stub for {@code serviceId} matching {@code matchKey}, or {@code null} if none. */
    StubRecord find(String serviceId, String matchKey) {
        for (StubRecord record : byService.getOrDefault(serviceId, List.of())) {
            if (record.matchKey().equals(matchKey)) {
                return record;
            }
        }
        return null;
    }

    public Set<String> allServiceIds() {
        return Collections.unmodifiableSet(byService.keySet());
    }
}

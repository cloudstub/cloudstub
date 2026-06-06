package io.cloudmock.standalone;

import io.cloudmock.core.spi.CloudMockService;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

final class ServiceDiscovery {

    private ServiceDiscovery() {}

    static List<String> discoverServiceIds() {
        List<String> ids = new ArrayList<>();
        ServiceLoader.load(CloudMockService.class, Thread.currentThread().getContextClassLoader())
                .forEach(s -> ids.add(s.serviceId()));
        return ids;
    }
}

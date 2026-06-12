package io.cloudstub.standalone;

import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.core.spi.CloudStubService;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

final class ServiceDiscovery {

    private ServiceDiscovery() {}

    static List<String> discoverServiceIds(ClassLoader classLoader) {
        List<String> ids = new ArrayList<>();
        ServiceLoader.load(CloudStubService.class, classLoader)
                .forEach(s -> ids.add(s.serviceId()));
        return ids;
    }

    static List<CloudStubApiService> discoverApiServices(ClassLoader classLoader) {
        List<CloudStubApiService> services = new ArrayList<>();
        ServiceLoader.load(CloudStubApiService.class, classLoader).forEach(services::add);
        return services;
    }
}

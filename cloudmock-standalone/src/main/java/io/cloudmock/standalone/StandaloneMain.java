package io.cloudmock.standalone;

import io.cloudmock.core.CloudMock;
import io.cloudmock.core.spi.CloudMockApiService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public final class StandaloneMain {

    public static void main(String[] args) throws Exception {
        int port = PortResolver.resolve(args);
        int apiPort = ApiPortResolver.resolve(args);
        int maxHistory = MaxHistoryResolver.resolve(args);
        List<String> available = ServiceDiscovery.discoverServiceIds();
        Set<String> requested = ModuleSelector.resolve(args);
        List<String> enabled = resolveEnabled(available, requested);
        Path storeDir = StoreDirectoryResolver.resolve(args);

        System.out.println("[CloudMock] Available modules: " + join(available));
        System.out.println("[CloudMock] Enabled modules: " + join(enabled));
        System.out.println("[CloudMock] State storage: "
                + (storeDir != null ? "persistent (" + storeDir + ")" : "in-memory (not persisted)"));
        System.out.println("[CloudMock] Request history: "
                + (maxHistory > 0 ? "last " + maxHistory + " entries" : "unlimited"));

        CloudMock cloudMock = new CloudMock().withPort(port).withMaxRequestHistory(maxHistory);
        if (requested != null) {
            cloudMock.withEnabledServices(enabled);
        }
        if (storeDir != null) {
            cloudMock.withStoreDirectory(storeDir);
        }

        // API routes must track the enabled modules: a disabled service has no stubs, so it must
        // not advertise REST routes (or CLI commands) either, otherwise the two views disagree.
        List<CloudMockApiService> apiServices = discoverApiServices().stream()
                .filter(svc -> enabled.contains(svc.serviceId()))
                .toList();

        try (cloudMock; ApiServer apiServer = new ApiServer(cloudMock, apiPort, apiServices)) {
            cloudMock.start();
            apiServer.start();

            System.out.println("CloudMock started on port " + cloudMock.port());
            System.out.println("CloudMock API on port " + apiPort);
            System.out.flush();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[CloudMock] Shutting down...");
                apiServer.stop();
                cloudMock.stop();
            }));

            Thread.currentThread().join();
        }
    }

    private static List<String> resolveEnabled(List<String> available, Set<String> requested) {
        if (requested == null) {
            return available;
        }
        List<String> unknown = requested.stream()
                .filter(id -> !available.contains(id))
                .toList();
        if (!unknown.isEmpty()) {
            System.err.println("[CloudMock] Unknown module(s): " + join(unknown)
                    + ". Available: " + join(available));
            System.exit(1);
        }
        return requested.stream().toList();
    }

    private static List<CloudMockApiService> discoverApiServices() {
        List<CloudMockApiService> services = new ArrayList<>();
        ServiceLoader.load(CloudMockApiService.class,
                Thread.currentThread().getContextClassLoader())
                .forEach(services::add);
        return services;
    }

    private static String join(Collection<String> ids) {
        return ids.isEmpty() ? "(none)" : String.join(", ", ids);
    }
}

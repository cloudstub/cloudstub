package io.cloudmock.standalone;

import io.cloudmock.core.CloudMock;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class StandaloneMain {

    public static void main(String[] args) throws InterruptedException {
        int port = PortResolver.resolve(args);
        List<String> available = ServiceDiscovery.discoverServiceIds();
        Set<String> requested = ModuleSelector.resolve(args);
        List<String> enabled = resolveEnabled(available, requested);
        Path storeDir = StoreDirectoryResolver.resolve(args);

        System.out.println("[CloudMock] Available modules: " + join(available));
        System.out.println("[CloudMock] Enabled modules: " + join(enabled));
        System.out.println("[CloudMock] State storage: "
                + (storeDir != null ? "persistent (" + storeDir + ")" : "in-memory (not persisted)"));

        CloudMock cloudMock = new CloudMock().withPort(port);
        if (requested != null) {
            cloudMock.withEnabledServices(enabled);
        }
        if (storeDir != null) {
            cloudMock.withStoreDirectory(storeDir);
        }

        try (cloudMock) {
            cloudMock.start();
            System.out.println("CloudMock started on port " + cloudMock.port());
            System.out.flush();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[CloudMock] Shutting down...");
                cloudMock.stop();
            }));

            Thread.currentThread().join();
        }
    }

    /**
     * Resolves the effective set of modules to enable. When no filter is requested, all
     * discovered modules are enabled. When a filter names a module that is not on the
     * classpath, the process fails fast with a clear message rather than silently serving
     * nothing for that module.
     */
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

    private static String join(Collection<String> ids) {
        return ids.isEmpty() ? "(none)" : String.join(", ", ids);
    }
}

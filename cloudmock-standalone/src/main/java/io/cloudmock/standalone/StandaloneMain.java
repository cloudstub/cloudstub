package io.cloudmock.standalone;

import io.cloudmock.core.CloudMock;

import java.util.List;

public final class StandaloneMain {

    public static void main(String[] args) throws InterruptedException {
        int port = PortResolver.resolve(args);

        List<String> modules = ServiceDiscovery.discoverServiceIds();
        if (modules.isEmpty()) {
            System.out.println("[CloudMock] No service modules found on classpath.");
        } else {
            System.out.println("[CloudMock] Discovered modules: " + String.join(", ", modules));
        }

        try (CloudMock cloudMock = new CloudMock().withPort(port)) {
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
}

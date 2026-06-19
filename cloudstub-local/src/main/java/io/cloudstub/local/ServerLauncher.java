package io.cloudstub.local;

import io.cloudstub.core.CloudStub;
import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.local.config.exception.LocalConfigException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Boots the standalone server: resolves the launcher configuration, provisions and loads module
 * jars, starts the mock and the API server, installs the shutdown hook, and blocks until the
 * process is terminated.
 */
final class ServerLauncher {

    private ServerLauncher() {}

    static void serve(String[] args) throws Exception {
        LauncherConfig config;
        try {
            config = LauncherConfig.resolve(args);
        } catch (LocalConfigException e) {
            System.err.println("[CloudStub] ERROR: " + e.getMessage());
            System.exit(1);
            return;
        }

        Path modulesDir = config.modulesDir;
        Set<String> requested = config.requested;

        if (requested != null && config.autoDownload) {
            modulesDir =
                    ModuleProvisioner.provisionMissing(
                            requested, modulesDir, config.moduleVersion, config.mavenBaseUrl);
        }

        ClassLoader pluginLoader = PluginLoader.load(modulesDir);

        List<String> available = ServiceDiscovery.discoverServiceIds(pluginLoader);
        List<String> enabled = resolveEnabled(available, requested, config.autoDownload);

        StartupBanner.configuration(
                modulesDir, available, enabled, config.storeDir, config.maxHistory);

        CloudStub cloudMock =
                new CloudStub().withPort(config.port).withMaxRequestHistory(config.maxHistory);
        // Always pass the enabled set (even when empty): the default is "no services", so the
        // filter must be applied unconditionally rather than falling back to "all discovered".
        cloudMock.withEnabledServices(enabled);
        if (config.storeDir != null) {
            cloudMock.withStoreDirectory(config.storeDir);
        }

        // API routes must track the enabled modules: a disabled service has no stubs, so it must
        // not advertise REST routes (or CLI commands) either, otherwise the two views disagree.
        List<CloudStubApiService> apiServices =
                ServiceDiscovery.discoverApiServices(pluginLoader).stream()
                        .filter(svc -> enabled.contains(svc.serviceId()))
                        .toList();

        // ModuleInitializer (inside CloudStub.start()) uses the thread's context classloader for
        // ServiceLoader discovery. Point it at the plugin classloader so the module jars loaded
        // from the plugin directory are visible when stubs are registered.
        Thread.currentThread().setContextClassLoader(pluginLoader);

        try (cloudMock;
                LocalServer localServer = new LocalServer(cloudMock, config.apiPort, apiServices)) {
            cloudMock.start();
            localServer.start();

            StartupBanner.ready(cloudMock.port(), config.apiPort);

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        System.out.println("[CloudStub] Shutting down...");
                                        localServer.stop();
                                        cloudMock.stop();
                                    }));

            Thread.currentThread().join();
        }
    }

    private static List<String> resolveEnabled(
            List<String> available, Set<String> requested, boolean autoDownload) {
        if (requested == null) {
            // No --services / CLOUDSTUB_SERVICES selection: load nothing. Services are opt-in,
            // matching embedded mode where only modules placed on the classpath load.
            return List.of();
        }
        List<String> unknown = requested.stream().filter(id -> !available.contains(id)).toList();
        if (!unknown.isEmpty()) {
            System.err.println(
                    "[CloudStub] Unknown service(s): "
                            + StartupBanner.join(unknown)
                            + ". Available: "
                            + StartupBanner.join(available));
            if (!autoDownload) {
                System.err.println(
                        "[CloudStub]          Auto-download is disabled. Drop the module jar into"
                                + " the plugin directory, or enable auto-download (omit"
                                + " --no-download / set CLOUDSTUB_AUTO_DOWNLOAD=true).");
            }
            System.exit(1);
        }
        return requested.stream().toList();
    }
}

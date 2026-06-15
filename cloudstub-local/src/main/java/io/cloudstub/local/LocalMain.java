package io.cloudstub.local;

import io.cloudstub.core.CloudStub;
import io.cloudstub.core.download.ModuleDownloadException;
import io.cloudstub.core.download.ModuleDownloader;
import io.cloudstub.core.spi.CloudStubApiService;
import io.cloudstub.local.config.LocalConfig;
import io.cloudstub.local.config.exception.LocalConfigException;
import io.cloudstub.local.config.resolver.ApiPortResolver;
import io.cloudstub.local.config.resolver.AutoDownloadResolver;
import io.cloudstub.local.config.resolver.MavenBaseUrlResolver;
import io.cloudstub.local.config.resolver.MaxHistoryResolver;
import io.cloudstub.local.config.resolver.ModuleVersionResolver;
import io.cloudstub.local.config.resolver.ModulesDirResolver;
import io.cloudstub.local.config.resolver.PortResolver;
import io.cloudstub.local.config.resolver.ServiceSelector;
import io.cloudstub.local.config.resolver.StoreDirectoryResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocalMain {

    // Debug detection runs before logger acquisition so the level takes effect when slf4j-simple
    // binds its configuration. CLOUDSTUB_DEBUG or -Dcloudstub.debug=true both work.
    static {
        if ("true".equalsIgnoreCase(System.getProperty("cloudstub.debug"))
                || "true".equalsIgnoreCase(System.getenv("CLOUDSTUB_DEBUG"))) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(LocalMain.class);

    public static void main(String[] args) throws Exception {
        Config resolved;
        try {
            resolved = Config.resolve(args);
        } catch (LocalConfigException e) {
            System.err.println("[CloudStub] ERROR: " + e.getMessage());
            System.exit(1);
            return; // unreachable; satisfies the compiler that resolved is assigned
        }

        int port = resolved.port;
        int apiPort = resolved.apiPort;
        int maxHistory = resolved.maxHistory;
        Path storeDir = resolved.storeDir;
        Path modulesDir = resolved.modulesDir;
        Set<String> requested = resolved.requested;

        if (requested != null && resolved.autoDownload) {
            modulesDir =
                    provisionMissing(
                            requested, modulesDir, resolved.moduleVersion, resolved.mavenBaseUrl);
        }

        ClassLoader pluginLoader = PluginLoader.load(modulesDir);

        List<String> available = ServiceDiscovery.discoverServiceIds(pluginLoader);
        List<String> enabled = resolveEnabled(available, requested, resolved.autoDownload);

        System.out.println(
                "[CloudStub] Plugin directory: "
                        + (modulesDir != null ? modulesDir.toAbsolutePath() : "(none)"));
        System.out.println("[CloudStub] Available services: " + join(available));
        System.out.println("[CloudStub] Enabled services: " + join(enabled));
        if (enabled.isEmpty()) {
            System.out.println(
                    "[CloudStub] WARNING: no services enabled — the mock will serve nothing.");
            System.out.println(
                    "[CloudStub]          Enable services with --services=<id>[,<id>...] "
                            + "or CLOUDSTUB_SERVICES=<id>[,<id>...].");
            System.out.println("[CloudStub]          Available services: " + join(available));
        }
        System.out.println(
                "[CloudStub] State storage: "
                        + (storeDir != null
                                ? "persistent (" + storeDir + ")"
                                : "in-memory (not persisted)"));
        System.out.println(
                "[CloudStub] Request history: "
                        + (maxHistory > 0 ? "last " + maxHistory + " entries" : "unlimited"));

        CloudStub cloudMock = new CloudStub().withPort(port).withMaxRequestHistory(maxHistory);
        // Always pass the enabled set (even when empty): the default is "no services", so the
        // filter must be applied unconditionally rather than falling back to "all discovered".
        cloudMock.withEnabledServices(enabled);
        if (storeDir != null) {
            cloudMock.withStoreDirectory(storeDir);
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
                ApiServer apiServer = new ApiServer(cloudMock, apiPort, apiServices)) {
            cloudMock.start();
            apiServer.start();

            System.out.println("CloudStub started on port " + cloudMock.port());
            System.out.println("CloudStub API on port " + apiPort);
            System.out.flush();

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        System.out.println("[CloudStub] Shutting down...");
                                        apiServer.stop();
                                        cloudMock.stop();
                                    }));

            Thread.currentThread().join();
        }
    }

    /**
     * The fully resolved launcher configuration, with each value already merged across CLI flag,
     * environment variable, config file, and default by its resolver. Resolution is collected here
     * so the single {@link LocalConfigException} a malformed config file may raise is caught in one
     * place.
     */
    private static final class Config {
        final int port;
        final int apiPort;
        final int maxHistory;
        final Path storeDir;
        final Path modulesDir;
        final Set<String> requested;
        final boolean autoDownload;
        final String moduleVersion;
        final String mavenBaseUrl;

        private Config(String[] args) {
            LocalConfig file = LocalConfig.load(args);
            this.port = PortResolver.resolve(args, file);
            this.apiPort = ApiPortResolver.resolve(args, file);
            this.maxHistory = MaxHistoryResolver.resolve(args, file);
            this.storeDir = StoreDirectoryResolver.resolve(args, file);
            this.modulesDir = ModulesDirResolver.resolve(args, file);
            this.requested = ServiceSelector.resolve(args, file);
            this.autoDownload = AutoDownloadResolver.isEnabled(args, file);
            this.moduleVersion = ModuleVersionResolver.resolve(args, file);
            this.mavenBaseUrl = MavenBaseUrlResolver.resolve(args, file);
        }

        static Config resolve(String[] args) {
            return new Config(args);
        }
    }

    /**
     * Provisions any requested service whose jar is absent from the plugin directory by downloading
     * it from Maven Central. Returns the directory the modules are loaded from — the resolved
     * directory, or the default {@code ./modules} created on demand when none was resolved.
     */
    private static Path provisionMissing(
            Set<String> requested, Path modulesDir, String version, String mavenBaseUrl) {
        Path targetDir = modulesDir != null ? modulesDir : Path.of(ModulesDirResolver.DEFAULT_DIR);
        ModuleDownloader downloader = new ModuleDownloader(mavenBaseUrl);
        List<String> downloaded = new ArrayList<>();
        for (String service : requested) {
            if (ModuleDownloader.isCached(targetDir, service, version)) {
                continue;
            }
            try {
                Path jar = downloader.download(service, version, targetDir);
                System.out.println(
                        "[CloudStub] Downloaded "
                                + ModuleDownloader.coordinate(service, version)
                                + " -> "
                                + jar.toAbsolutePath());
                downloaded.add(service);
            } catch (ModuleDownloadException e) {
                System.err.println("[CloudStub] ERROR: " + e.getMessage());
                System.exit(1);
            }
        }
        if (modulesDir == null && !downloaded.isEmpty()) {
            return targetDir;
        }
        return modulesDir;
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
                            + join(unknown)
                            + ". Available: "
                            + join(available));
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

    private static String join(Collection<String> ids) {
        return ids.isEmpty() ? "(none)" : String.join(", ", ids);
    }
}

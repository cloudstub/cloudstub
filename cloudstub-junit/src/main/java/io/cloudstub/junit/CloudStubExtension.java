package io.cloudstub.junit;

import io.cloudstub.core.CloudStub;
import io.cloudstub.core.download.CoreVersion;
import io.cloudstub.core.download.ModuleDownloader;
import io.cloudstub.core.spi.CloudStubService;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension (JUnit 5 and 6) that manages the {@link CloudStub} lifecycle around a test class
 * and applies fault injection annotations ({@link SimulateThrottle}, {@link SimulateTimeout},
 * {@link SimulateNetworkBrownout}) to individual test methods.
 *
 * <h2>Usage - zero-boilerplate ({@code @ExtendWith})</h2>
 *
 * <pre>
 * {@literal @}ExtendWith(CloudStubExtension.class)
 * class MyTest { ... }
 * </pre>
 *
 * <h2>Usage — port access ({@code @RegisterExtension})</h2>
 *
 * <pre>
 * {@literal @}RegisterExtension
 * static CloudStubExtension cloudMock = new CloudStubExtension()
 *         .withService(new CloudStubSqsService());
 *
 * {@literal @}Test
 * void myTest() {
 *     int port = cloudMock.port();
 * }
 * </pre>
 *
 * <h2>Auto-downloaded modules ({@code withModules})</h2>
 *
 * <pre>
 * {@literal @}RegisterExtension
 * static CloudStubExtension cloudMock = new CloudStubExtension()
 *         .withModules("sqs", "secretsmanager");
 * </pre>
 *
 * <p>The named module jars are fetched from Maven Central during {@code beforeAll} and registered,
 * so the modules need not be on the test classpath. {@link #withService} registers an in-process
 * instance (for stubs that are not published artifacts); {@link #withModule}/{@link #withModules}
 * downloads a published module by id. Downloads are cached under {@code ~/.cloudstub/modules} by
 * default, so a module fetched once is reused across runs and JVMs. A module that ships a
 * client-side AWS SDK component (currently only S3) cannot be provisioned by download and must be a
 * test dependency; see the JUnit Extension reference for details.
 *
 * <h2>Fault injection</h2>
 *
 * <pre>
 * {@literal @}SimulateThrottle(service = "sqs")
 * {@literal @}Test
 * void throttledTest() { ... }
 * </pre>
 *
 * <p>Fault state is always cleaned up after each test method, even if the test throws. Each test
 * class gets an independent {@code CloudStub} instance — one class stopping never affects another
 * class's running instance.
 */
public final class CloudStubExtension
        implements BeforeAllCallback,
                AfterAllCallback,
                BeforeTestExecutionCallback,
                AfterTestExecutionCallback {

    private static final Path DEFAULT_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".cloudstub", "modules");

    private final List<CloudStubService> services = new ArrayList<>();
    private final Set<String> moduleIds = new LinkedHashSet<>();
    private String moduleVersion;
    private Path modulesCacheDir = DEFAULT_CACHE_DIR;
    private String mavenBaseUrl;
    private CloudStub cloudMock;

    /**
     * Registers a service implementation to be installed when CloudStub starts. Use this for stubs
     * that are not published artifacts (custom or test-local services). Must be called before the
     * extension starts (i.e. before any test runs).
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withService(CloudStubService service) {
        services.add(service);
        return this;
    }

    /**
     * Registers several service implementations at once; the plural of {@link
     * #withService(CloudStubService)}. Must be called before the extension starts.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withServices(CloudStubService... services) {
        this.services.addAll(Arrays.asList(services));
        return this;
    }

    /**
     * Records a published service module to auto-download by id. The module jar ({@code
     * io.github.cloudstub:cloudstub-<serviceId>}) is fetched from Maven Central during {@code
     * beforeAll} if it is not already cached, then registered without needing the module on the
     * test classpath. Repeated ids collapse to a single fetch. Must be called before the extension
     * starts.
     *
     * @param serviceId service id (e.g. {@code sqs})
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withModule(String serviceId) {
        moduleIds.add(serviceId);
        return this;
    }

    /**
     * Records several published service modules to auto-download by id; the plural of {@link
     * #withModule(String)}. Must be called before the extension starts.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withModules(String... serviceIds) {
        moduleIds.addAll(Arrays.asList(serviceIds));
        return this;
    }

    /**
     * Records several published service modules to auto-download by id; the plural of {@link
     * #withModule(String)}. Must be called before the extension starts.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withModules(List<String> serviceIds) {
        moduleIds.addAll(serviceIds);
        return this;
    }

    /**
     * Sets the version of the modules requested via {@link #withModule}/{@link #withModules}.
     * Defaults to {@link CoreVersion#current()}.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withModuleVersion(String version) {
        this.moduleVersion = version;
        return this;
    }

    /**
     * Sets the directory used to cache downloaded module jars. Defaults to {@code
     * ~/.cloudstub/modules}. A persistent directory lets a module fetched once be reused across
     * test classes, runs, and JVMs.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withModulesCacheDir(Path cacheDir) {
        this.modulesCacheDir = cacheDir;
        return this;
    }

    /**
     * Sets the Maven repository base URL module jars are downloaded from. Defaults to {@link
     * ModuleDownloader#CENTRAL_BASE_URL Maven Central}.
     *
     * @return {@code this} for fluent chaining
     */
    public CloudStubExtension withMavenBaseUrl(String mavenBaseUrl) {
        this.mavenBaseUrl = mavenBaseUrl;
        return this;
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext context) {
        cloudMock = new CloudStub();
        services.forEach(cloudMock::withService);
        provisionModules();
        cloudMock.start();
    }

    /**
     * Provisions the requested modules. A requested id already discoverable on the application
     * classpath is left to {@code CloudStub.start()}'s own {@code ServiceLoader} (so its classpath
     * copy, including any AWS-SDK-side component such as the S3 virtual-host interceptor, stays
     * active and it is not registered twice). Each remaining id is downloaded if not cached and
     * registered from a classloader over its jar.
     */
    private void provisionModules() {
        if (moduleIds.isEmpty()) {
            return;
        }
        Set<String> onClasspath = serviceIds(contextClassLoader());
        Set<String> toDownload = new LinkedHashSet<>();
        for (String id : moduleIds) {
            if (!onClasspath.contains(id)) {
                toDownload.add(id);
            }
        }
        if (toDownload.isEmpty()) {
            return;
        }
        String version = moduleVersion != null ? moduleVersion : CoreVersion.current();
        ModuleDownloader downloader =
                mavenBaseUrl != null ? new ModuleDownloader(mavenBaseUrl) : new ModuleDownloader();
        List<URL> jarUrls = new ArrayList<>();
        for (String id : toDownload) {
            Path jar =
                    ModuleDownloader.isCached(modulesCacheDir, id, version)
                            ? ModuleDownloader.cachedJar(modulesCacheDir, id)
                            : downloader.download(id, version, modulesCacheDir);
            try {
                jarUrls.add(jar.toUri().toURL());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        ClassLoader moduleLoader =
                new URLClassLoader(jarUrls.toArray(new URL[0]), contextClassLoader());
        Set<String> registered = new LinkedHashSet<>();
        for (CloudStubService service : ServiceLoader.load(CloudStubService.class, moduleLoader)) {
            String id = service.serviceId();
            if (toDownload.contains(id) && registered.add(id)) {
                cloudMock.withService(service);
            }
        }
    }

    /** The thread context classloader, falling back to this class's classloader. */
    private ClassLoader contextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : getClass().getClassLoader();
    }

    /** Service ids discoverable via {@code ServiceLoader} on {@code classLoader}. */
    private static Set<String> serviceIds(ClassLoader classLoader) {
        Set<String> ids = new LinkedHashSet<>();
        for (CloudStubService service : ServiceLoader.load(CloudStubService.class, classLoader)) {
            ids.add(service.serviceId());
        }
        return ids;
    }

    @Override
    public void afterAll(@NonNull ExtensionContext context) {
        if (cloudMock != null) {
            cloudMock.stop();
            cloudMock = null;
        }
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        FaultInjectionExtension.applyFaults(context.getRequiredTestMethod(), cloudMock);
    }

    @Override
    public void afterTestExecution(@NonNull ExtensionContext context) {
        cloudMock.clearAllFaults();
    }

    /**
     * Returns the port the embedded server is listening on. Only valid inside a test method (after
     * {@code beforeAll} and before {@code afterAll}).
     */
    public int port() {
        return cloudMock.port();
    }
}

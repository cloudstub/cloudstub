package io.cloudstub.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.StateStore;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and registers all {@link CloudStubService} modules against a running server.
 *
 * <p>Combines {@link ServiceLoader} discovery (filtered by the enabled-service set) with any
 * explicitly registered modules, wiring each through a shared {@link WireMockStubRegistrar} and
 * {@link CloudStubContextImpl}. The resulting registrar is handed back for the engine to expose.
 */
public final class ModuleInitializer {

    private static final Logger log = LoggerFactory.getLogger(ModuleInitializer.class);

    private ModuleInitializer() {}

    public static WireMockStubRegistrar initialize(
            WireMockServer server,
            CloudStubSettings settings,
            StateStore stateStore,
            CloudStubResponseTransformer transformer,
            ServiceRegistry registry) {
        WireMockStubRegistrar registrar = new WireMockStubRegistrar(server, transformer, registry);
        CloudStubContextImpl context = new CloudStubContextImpl(registrar, stateStore);

        Set<String> enabled = settings.enabledServiceIds();
        ServiceLoader.load(CloudStubService.class, Thread.currentThread().getContextClassLoader())
                .forEach(
                        service -> {
                            if (enabled != null && !enabled.contains(service.serviceId())) {
                                return;
                            }
                            register(registrar, context, service, registry);
                        });
        settings.explicitServices()
                .forEach(service -> register(registrar, context, service, registry));

        return registrar;
    }

    private static void register(
            WireMockStubRegistrar registrar,
            CloudStubContextImpl context,
            CloudStubService service,
            ServiceRegistry registry) {
        String id = service.serviceId();
        registrar.setCurrentService(id);
        service.register(context);
        int stubCount = registry.getStubs(id).size();
        log.info("Registered module: {} ({} stub(s))", id, stubCount);
        if (log.isDebugEnabled()) {
            registry.getStubs(id).forEach(s -> log.debug("  {} {}", s.protocol(), s.matchKey()));
        }
    }
}

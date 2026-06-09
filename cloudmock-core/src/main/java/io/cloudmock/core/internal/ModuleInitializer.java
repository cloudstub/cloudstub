package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StateStore;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers and registers all {@link CloudMockService} modules against a running server.
 *
 * <p>Combines {@link ServiceLoader} discovery (filtered by the enabled-service set) with any
 * explicitly registered modules, wiring each through a shared {@link WireMockStubRegistrar} and
 * {@link CloudMockContextImpl}. The resulting registrar and fault engine are handed back for the
 * engine to expose.
 */
public final class ModuleInitializer {

    private ModuleInitializer() {}

    /** The collaborators produced while registering modules. */
    public record Result(WireMockStubRegistrar registrar, FaultEngine faultEngine) {}

    public static Result initialize(WireMockServer server, CloudMockSettings settings,
                                    StateStore stateStore, StatefulResponseTransformer stateful) {
        WireMockStubRegistrar registrar = new WireMockStubRegistrar(server, stateful);
        FaultEngine faultEngine = registrar.newFaultEngine();
        CloudMockContextImpl context = new CloudMockContextImpl(registrar, stateStore);

        Set<String> enabled = settings.enabledServiceIds();
        ServiceLoader.load(CloudMockService.class, Thread.currentThread().getContextClassLoader())
                .forEach(service -> {
                    if (enabled != null && !enabled.contains(service.serviceId())) {
                        return;
                    }
                    register(registrar, context, service);
                });
        settings.explicitServices().forEach(service -> register(registrar, context, service));

        return new Result(registrar, faultEngine);
    }

    private static void register(WireMockStubRegistrar registrar, CloudMockContextImpl context,
                                 CloudMockService service) {
        registrar.setCurrentService(service.serviceId());
        service.register(context);
    }
}

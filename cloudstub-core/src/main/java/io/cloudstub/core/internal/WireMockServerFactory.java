package io.cloudstub.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Builds and starts the embedded WireMock server from {@link CloudStubSettings}.
 *
 * <p>Owns every WireMock-configuration detail — global templating, the CloudStub Handlebars helpers
 * and transformers, the request-journal cap, and port selection — so that CloudStub itself never
 * touches a {@link WireMockConfiguration}.
 */
public final class WireMockServerFactory {

    private WireMockServerFactory() {}

    /**
     * Builds a WireMock server from {@code settings}, starts it, and returns it.
     *
     * @param transformer the shared global transformer that runs stateful stub handlers and applies
     *     faults; registered as a WireMock extension so it can reach the state store
     */
    public static WireMockServer createStarted(
            CloudStubSettings settings, CloudStubResponseTransformer transformer) {
        WireMockServer server = new WireMockServer(config(settings, transformer));
        server.start();
        return server;
    }

    private static WireMockConfiguration config(
            CloudStubSettings settings, CloudStubResponseTransformer transformer) {
        WireMockConfiguration config =
                WireMockConfiguration.options()
                        .globalTemplating(true)
                        .extensions(new Md5HandlebarsHelper(), transformer);
        if (settings.maxRequestHistory() > 0) {
            config.maxRequestJournalEntries(settings.maxRequestHistory());
        }
        if (settings.port() > 0) {
            config.port(settings.port());
        } else {
            config.dynamicPort();
        }
        return config;
    }
}

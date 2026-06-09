package io.cloudmock.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Builds and starts the embedded WireMock server from {@link CloudMockSettings}.
 *
 * <p>Owns every WireMock-configuration detail — global templating, the CloudMock Handlebars
 * helpers and transformers, the request-journal cap, and port selection — so that CloudMock
 * itself never touches a {@link WireMockConfiguration}.
 */
public final class WireMockServerFactory {

    private WireMockServerFactory() {}

    /**
     * Creates a started WireMock server configured per {@code settings}.
     *
     * @param stateful the shared transformer that runs stateful stub handlers; registered as a
     *                 WireMock extension so handler-based stubs can reach the state store
     */
    public static WireMockServer createStarted(CloudMockSettings settings,
                                               StatefulResponseTransformer stateful) {
        WireMockServer server = new WireMockServer(config(settings, stateful));
        server.start();
        return server;
    }

    private static WireMockConfiguration config(CloudMockSettings settings,
                                                StatefulResponseTransformer stateful) {
        WireMockConfiguration config = WireMockConfiguration.options()
                .globalTemplating(true)
                .extensions(new Md5HandlebarsHelper(), new BrownoutTransformer(), stateful);
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

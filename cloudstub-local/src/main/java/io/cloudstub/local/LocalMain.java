package io.cloudstub.local;

import io.cloudstub.local.cli.CliDispatch;
import io.cloudstub.local.cli.CloudStubCli;

public final class LocalMain {

    // Debug detection runs before logger acquisition so the level takes effect when slf4j-simple
    // binds its configuration. CLOUDSTUB_DEBUG or -Dcloudstub.debug=true both work.
    static {
        if ("true".equalsIgnoreCase(System.getProperty("cloudstub.debug"))
                || "true".equalsIgnoreCase(System.getenv("CLOUDSTUB_DEBUG"))) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
    }

    public static void main(String[] args) throws Exception {
        // Dual-mode: a command token (status, reset, sqs send-message, …) runs the CLI against a
        // running instance; no token — or an explicit `serve` — boots the server. The CLI path must
        // not touch CloudStub/WireMock classes, so they stay unloaded and CLI startup stays snappy.
        if (CliDispatch.isCliInvocation(args)) {
            System.exit(CloudStubCli.run(args));
            return;
        }
        ServerLauncher.serve(CliDispatch.stripServe(args));
    }
}

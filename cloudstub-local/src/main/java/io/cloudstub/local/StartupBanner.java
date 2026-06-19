package io.cloudstub.local;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

final class StartupBanner {

    private StartupBanner() {}

    /** Prints the resolved configuration: plugin directory, services, state storage, history. */
    static void configuration(
            Path modulesDir,
            List<String> available,
            List<String> enabled,
            Path storeDir,
            int maxHistory) {
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
    }

    /**
     * Prints the post-start readiness lines, emitted only after all module stubs are registered.
     */
    static void ready(int port, int apiPort) {
        System.out.println("CloudStub started on port " + port);
        System.out.println("CloudStub API on port " + apiPort);
        System.out.flush();
    }

    static String join(Collection<String> ids) {
        return ids.isEmpty() ? "(none)" : String.join(", ", ids);
    }
}

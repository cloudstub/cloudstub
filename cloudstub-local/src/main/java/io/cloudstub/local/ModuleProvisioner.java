package io.cloudstub.local;

import io.cloudstub.core.download.ModuleDownloadException;
import io.cloudstub.core.download.ModuleDownloader;
import io.cloudstub.local.config.resolver.ModulesDirResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ModuleProvisioner {

    private ModuleProvisioner() {}

    /**
     * Provisions any requested service whose jar is absent from the plugin directory by downloading
     * it from Maven Central. Returns the directory the modules are loaded from — the resolved
     * directory, or the default {@code ./modules} created on demand when none was resolved.
     */
    static Path provisionMissing(
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
                        "[CloudStub] Provisioned service '"
                                + service
                                + "' -> "
                                + jar.toAbsolutePath());
                downloaded.add(service);
            } catch (ModuleDownloadException e) {
                Path cached = ModuleDownloader.cachedJar(targetDir, service);
                if (cached != null) {
                    System.out.println(
                            "[CloudStub] WARNING: could not provision service '"
                                    + service
                                    + "' ("
                                    + e.getMessage()
                                    + "); using already-cached "
                                    + cached.toAbsolutePath());
                    continue;
                }
                System.err.println("[CloudStub] ERROR: " + e.getMessage());
                System.exit(1);
            }
        }
        if (modulesDir == null && !downloaded.isEmpty()) {
            return targetDir;
        }
        return modulesDir;
    }
}

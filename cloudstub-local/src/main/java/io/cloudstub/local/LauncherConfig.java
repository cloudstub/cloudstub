package io.cloudstub.local;

import io.cloudstub.local.config.LocalConfig;
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
import java.util.Set;

/**
 * The fully resolved launcher configuration: every option settled through its resolver against the
 * precedence chain (CLI flag → env var → config file → default).
 */
final class LauncherConfig {

    final LocalConfig file;
    final int port;
    final int apiPort;
    final int maxHistory;
    final Path storeDir;
    final Path modulesDir;
    final Set<String> requested;
    final boolean autoDownload;
    final String moduleVersion;
    final String mavenBaseUrl;

    private LauncherConfig(String[] args) {
        this.file = LocalConfig.load(args);
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

    static LauncherConfig resolve(String[] args) {
        return new LauncherConfig(args);
    }
}

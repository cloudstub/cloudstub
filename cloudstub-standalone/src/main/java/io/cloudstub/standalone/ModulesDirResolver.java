package io.cloudstub.standalone;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the directory from which module jars are loaded at startup.
 *
 * <p>Precedence: {@code --modules-dir=<path>} CLI flag, then {@code CLOUDSTUB_MODULES_DIR}
 * environment variable, then the {@link #DEFAULT_DIR default directory} ({@code ./modules}).
 *
 * <p>An explicitly provided path that does not exist causes a fast failure. The default directory
 * being absent (or empty) is not fatal — the server starts with no loaded modules.
 */
final class ModulesDirResolver {

    static final String DEFAULT_DIR = "modules";

    private ModulesDirResolver() {}

    /**
     * @return the resolved modules directory, or {@code null} when the default directory is absent
     */
    static Path resolve(String[] args) {
        String explicit = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--modules-dir=")) {
                explicit = args[i].substring("--modules-dir=".length());
                break;
            }
            if ("--modules-dir".equals(args[i]) && i + 1 < args.length) {
                explicit = args[i + 1];
                break;
            }
        }
        // A blank value (e.g. `--modules-dir=`) is treated as "not provided" rather than the empty
        // path, which Path.of("") resolves to the current working directory. Fall through to the
        // env var and then the default, matching StoreDirectoryResolver's handling of blank input.
        if (explicit != null && explicit.isBlank()) {
            explicit = null;
        }
        if (explicit == null) {
            String env = System.getenv("CLOUDSTUB_MODULES_DIR");
            if (env != null && !env.isBlank()) {
                explicit = env.trim();
            }
        }
        if (explicit != null) {
            Path dir = Path.of(explicit.trim());
            if (!Files.isDirectory(dir)) {
                System.err.println(
                        "[CloudStub] ERROR: modules directory '"
                                + dir
                                + "' does not exist or is not a directory.");
                System.exit(1);
            }
            return dir;
        }
        Path defaultDir = Path.of(DEFAULT_DIR);
        return Files.isDirectory(defaultDir) ? defaultDir : null;
    }
}

package io.cloudstub.standalone;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads module jars from a plugin directory into a {@link URLClassLoader} whose parent is the
 * current thread's context classloader, so that {@code io.cloudstub.core.spi} types resolve to the
 * same classes the server's core uses.
 */
final class PluginLoader {

    private PluginLoader() {}

    /**
     * Returns a classloader that covers all {@code .jar} files found in {@code modulesDir}, or the
     * current thread's context classloader when {@code modulesDir} is {@code null}.
     *
     * @param modulesDir directory to scan; {@code null} means no plugin directory was resolved
     * @return a classloader suitable for {@link java.util.ServiceLoader} discovery
     */
    static ClassLoader load(Path modulesDir) throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (modulesDir == null) {
            return parent;
        }
        List<URL> urls = new ArrayList<>();
        try (Stream<Path> entries = Files.list(modulesDir)) {
            entries.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(
                            p -> {
                                try {
                                    urls.add(p.toUri().toURL());
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }
}

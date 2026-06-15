package io.cloudstub.core.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads Handlebars response templates bundled as classpath resources in a module's JAR.
 *
 * <p>Generated service modules call {@link #load(Class, String)} at each registration site to read
 * the {@code .hbs} template for an operation. The template is resolved as {@code
 * /templates/<name>.hbs} relative to the supplied anchor class's classloader.
 */
public final class StubTemplates {

    private StubTemplates() {}

    /**
     * Reads the template {@code /templates/<name>.hbs} from the classpath of {@code anchor} and
     * returns its trimmed UTF-8 contents.
     *
     * @param anchor the class whose classloader resolves the template resource (typically the
     *     calling service class)
     * @param name the template base name, without the {@code /templates/} prefix or {@code .hbs}
     *     suffix
     * @return the template contents, decoded as UTF-8 and trimmed of leading/trailing whitespace
     * @throws IllegalStateException if no template resource exists at the resolved path
     * @throws UncheckedIOException if the resource cannot be read
     */
    public static String load(Class<?> anchor, String name) {
        String path = "/templates/" + name + ".hbs";
        try (InputStream in = anchor.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Template not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

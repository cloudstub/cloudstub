package io.cloudstub.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serves a single-page web app (the CloudStub Console) bundled as classpath resources.
 *
 * <p>Mounted at a base path (e.g. {@code /console}); a request for {@code <mount>/<rel>} is served
 * from the classpath resource {@code <resourceRoot>/<rel>}. A request for an extensionless path
 * that has no matching resource falls back to {@code index.html} so the SPA's client-side router
 * can handle it (deep links, reloads). A request that exactly equals the mount path (no trailing
 * slash) redirects to {@code <mount>/} so the document's relative asset URLs resolve against the
 * app's base href.
 *
 * <p>Only {@code GET} is served. Path traversal ({@code ..}) is rejected.
 */
final class ConsoleHandler implements HttpHandler {

    private final String mountPath;
    private final String resourceRoot;

    /**
     * @param mountPath the URL prefix the app is served under, without a trailing slash (e.g.
     *     {@code /console})
     * @param resourceRoot the classpath directory the built assets live in, without leading or
     *     trailing slash (e.g. {@code console})
     */
    ConsoleHandler(String mountPath, String resourceRoot) {
        this.mountPath = mountPath;
        this.resourceRoot = resourceRoot;
    }

    /**
     * Whether the app's {@code index.html} is present on the classpath (i.e. the UI is bundled).
     */
    boolean isAvailable() {
        return ConsoleHandler.class.getResource("/" + resourceRoot + "/index.html") != null;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(
                    exchange,
                    405,
                    "text/plain",
                    "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        // /console -> /console/ so the app's base href ("/console/") resolves relative asset URLs.
        if (path.equals(mountPath)) {
            redirect(exchange, mountPath + "/");
            return;
        }

        String rel = path.substring(mountPath.length()); // begins with "/"
        if (rel.equals("/")) {
            rel = "/index.html";
        }
        if (rel.contains("..")) {
            send(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        byte[] body = read(rel);
        if (body == null) {
            // No such asset. An extensionless path is a client-side route → serve index.html so the
            // SPA boots and routes it; anything else (a missing .js/.css/…) is a real 404.
            if (lastSegmentHasExtension(rel)) {
                send(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            rel = "/index.html";
            body = read(rel);
            if (body == null) {
                send(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
                return;
            }
        }
        send(exchange, 200, contentType(rel), body);
    }

    private byte[] read(String rel) throws IOException {
        try (InputStream in = ConsoleHandler.class.getResourceAsStream("/" + resourceRoot + rel)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static boolean lastSegmentHasExtension(String rel) {
        int slash = rel.lastIndexOf('/');
        return rel.indexOf('.', slash + 1) >= 0;
    }

    private static final Map<String, String> CONTENT_TYPES =
            Map.ofEntries(
                    Map.entry("html", "text/html; charset=utf-8"),
                    Map.entry("js", "text/javascript"),
                    Map.entry("mjs", "text/javascript"),
                    Map.entry("css", "text/css"),
                    Map.entry("json", "application/json"),
                    Map.entry("webmanifest", "application/manifest+json"),
                    Map.entry("ico", "image/x-icon"),
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("svg", "image/svg+xml"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("woff", "font/woff"),
                    Map.entry("woff2", "font/woff2"),
                    Map.entry("ttf", "font/ttf"),
                    Map.entry("map", "application/json"),
                    Map.entry("txt", "text/plain; charset=utf-8"));

    private static String contentType(String rel) {
        int dot = rel.lastIndexOf('.');
        String ext = dot >= 0 ? rel.substring(dot + 1).toLowerCase() : "";
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }
}

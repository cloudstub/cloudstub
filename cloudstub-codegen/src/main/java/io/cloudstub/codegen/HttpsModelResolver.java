package io.cloudstub.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class HttpsModelResolver implements ModelResolver {

    static final Path DOWNLOAD_DIR = Path.of("build", ".cloudstub", "codegen");

    private final URI uri;

    HttpsModelResolver(String url) {
        this.uri = URI.create(rewriteGitHubBlobUrl(url));
    }

    @Override
    public Path resolve() throws IOException {
        String uriPath = uri.getPath();
        String filename = uriPath.substring(uriPath.lastIndexOf('/') + 1);
        validateExtension(filename);

        Path dir = DOWNLOAD_DIR.toAbsolutePath();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);

        System.out.println("Downloading model from: " + uri);
        // Download to a temp file in the same directory, then atomically move it into place, so an
        // interrupted download never leaves a partial model at the target path.
        Path partial = Files.createTempFile(dir, filename + "-", ".part");
        try (InputStream in = uri.toURL().openStream()) {
            Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                    partial,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
        return target;
    }

    // Rewrites a github.com blob URL to its raw.githubusercontent.com equivalent, which serves the
    // file bytes. Other https URLs pass through unchanged.
    static String rewriteGitHubBlobUrl(String url) {
        String prefix = "https://github.com/";
        String marker = "/blob/";
        if (!url.startsWith(prefix)) return url;
        int markerIndex = url.indexOf(marker);
        if (markerIndex < 0) return url;
        String ownerRepo = url.substring(prefix.length(), markerIndex);
        String refAndPath = stripQueryAndFragment(url.substring(markerIndex + marker.length()));
        return "https://raw.githubusercontent.com/" + ownerRepo + "/" + refAndPath;
    }

    private static String stripQueryAndFragment(String path) {
        int cut = path.length();
        int query = path.indexOf('?');
        if (query >= 0) cut = query;
        int fragment = path.indexOf('#');
        if (fragment >= 0 && fragment < cut) cut = fragment;
        return path.substring(0, cut);
    }

    private static void validateExtension(String filename) {
        if (!filename.endsWith(".smithy") && !filename.endsWith(".json")) {
            throw new IllegalArgumentException(
                    "URL must point to a .smithy or .json file, got: " + filename);
        }
    }
}

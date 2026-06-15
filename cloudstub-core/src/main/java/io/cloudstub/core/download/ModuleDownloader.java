package io.cloudstub.core.download;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Downloads a CloudStub service module jar from Maven Central into a plugin directory.
 *
 * <p>Modules are self-contained ({@code compileOnly} on core, no runtime transitive dependencies),
 * so a single direct jar download per service is sufficient — this is not a transitive dependency
 * resolver. The network surface is one fixed Central base URL; no arbitrary repositories.
 *
 * <p>A downloaded jar is checksum-verified (the strongest published of SHA-512 / SHA-256 / SHA-1)
 * before it is moved into place; a mismatch, a missing checksum, or any transport error fails with
 * an actionable {@link ModuleDownloadException}.
 *
 * <p>Instances are thread-safe.
 */
public final class ModuleDownloader {

    /** Canonical Maven Central artifact base. */
    public static final String CENTRAL_BASE_URL = "https://repo1.maven.org/maven2";

    /** Published Maven group shared by every CloudStub module. */
    public static final String GROUP = "io.github.cloudstub";

    // Strongest first: Central always publishes .sha1 and often .sha256/.sha512. The first
    // checksum that can be retrieved is used; if none can be, the download is rejected.
    private static final List<String> CHECKSUM_EXTENSIONS = List.of("sha512", "sha256", "sha1");

    private final String baseUrl;
    private final HttpClient http;

    /** Creates a downloader that resolves from {@link #CENTRAL_BASE_URL Maven Central}. */
    public ModuleDownloader() {
        this(CENTRAL_BASE_URL);
    }

    /**
     * Creates a downloader resolving from {@code baseUrl} (a Maven repository layout root).
     * Intended for pointing at a corporate mirror or, in tests, a local repository.
     *
     * @param baseUrl repository base URL; a trailing slash is tolerated
     */
    public ModuleDownloader(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    /**
     * @return the Maven coordinate string for a service at a version
     */
    public static String coordinate(String service, String version) {
        return GROUP + ":cloudstub-" + service + ":" + version;
    }

    /**
     * @return the module jar filename for a service at a version, e.g. {@code
     *     cloudstub-sqs-0.1.0.jar}
     */
    public static String jarFileName(String service, String version) {
        return "cloudstub-" + service + "-" + version + ".jar";
    }

    /**
     * Returns whether the jar for {@code service} at {@code version} is already present in {@code
     * dir} — either the exact versioned jar ({@code cloudstub-<service>-<version>.jar}) or a
     * user-placed unversioned jar ({@code cloudstub-<service>.jar}). The match is version-specific
     * so that a different cached version does not satisfy a request for {@code version}: after a
     * core upgrade the requested version is fetched rather than the stale one being reused.
     *
     * @param dir plugin directory to inspect; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @param version requested module version
     * @return {@code true} if the requested version (or a user-placed unversioned jar) is present
     */
    public static boolean isCached(Path dir, String service, String version) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        String exactVersioned = jarFileName(service, version);
        String unversioned = "cloudstub-" + service + ".jar";
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString())
                    .anyMatch(n -> n.equals(exactVersioned) || n.equals(unversioned));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Removes any versioned jar for {@code service} in {@code dir} other than {@code keepFileName},
     * so a freshly downloaded version does not coexist with a stale one — two versioned jars of the
     * same module on the plugin classloader would register the service twice. A user-placed
     * unversioned {@code cloudstub-<service>.jar} is left untouched (it is an explicit manual
     * choice, not a cache entry). A removal that fails is ignored: a leftover stale jar is a lesser
     * problem than failing a successful download.
     *
     * @param dir plugin directory to prune; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @param keepFileName the just-written jar filename to retain
     */
    public static void removeOtherVersions(Path dir, String service, String keepFileName) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        String versionedPrefix = "cloudstub-" + service + "-";
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path p : entries.toList()) {
                String n = p.getFileName().toString();
                if (n.endsWith(".jar")
                        && n.startsWith(versionedPrefix)
                        && !n.equals(keepFileName)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // leave the stale jar in place rather than failing
                    }
                }
            }
        } catch (IOException ignored) {
            // directory not listable — nothing to prune
        }
    }

    /**
     * Downloads the module jar for {@code service} at {@code version} into {@code dir}, verifying
     * its checksum before the file is made visible.
     *
     * @param service service id (e.g. {@code sqs})
     * @param version module version to fetch
     * @param dir target plugin directory (created if absent)
     * @return the path the jar was written to
     * @throws ModuleDownloadException with an actionable message on any failure
     */
    public Path download(String service, String version, Path dir) {
        String coordinate = coordinate(service, version);
        byte[] jarBytes = fetchJar(service, version, dir, coordinate);
        verifyChecksum(service, version, dir, coordinate, jarBytes);

        String jarName = jarFileName(service, version);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(jarName);
            // Write to a temp file then move so a crashed download never leaves a partial
            // jar that a later run would treat as a valid cache hit.
            Path tmp = Files.createTempFile(dir, jarName, ".part");
            try {
                Files.write(tmp, jarBytes);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } finally {
                // A failed move leaves the temp file behind; remove it so .part files do not
                // accumulate. After a successful move the temp path is gone and this is a no-op.
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup; never turn a successful download into a failure
                }
            }
        } catch (IOException e) {
            throw new ModuleDownloadException(
                    fail(service, coordinate, dir, "the jar could not be written (" + e + ")"));
        }
    }

    private byte[] fetchJar(String service, String version, Path dir, String coordinate) {
        String jarUrl = artifactUrl(service, version, "jar");
        try {
            return fetch(jarUrl);
        } catch (NotFoundException e) {
            throw new ModuleDownloadException(
                    fail(
                            service,
                            coordinate,
                            dir,
                            "no artifact was found at "
                                    + jarUrl
                                    + " (the service name may be wrong, or that version is not"
                                    + " published)"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModuleDownloadException(
                    fail(service, coordinate, dir, "the download was interrupted"));
        } catch (IOException e) {
            throw new ModuleDownloadException(
                    fail(
                            service,
                            coordinate,
                            dir,
                            "the download failed ("
                                    + e.getMessage()
                                    + ") — check your network connection"));
        }
    }

    private void verifyChecksum(
            String service, String version, Path dir, String coordinate, byte[] jarBytes) {
        for (String ext : CHECKSUM_EXTENSIONS) {
            byte[] checksumBytes;
            try {
                checksumBytes = fetch(artifactUrl(service, version, "jar." + ext));
            } catch (NotFoundException e) {
                continue; // this algorithm is not published; try a weaker one
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModuleDownloadException(
                        fail(service, coordinate, dir, "checksum retrieval was interrupted"));
            } catch (IOException e) {
                // A transport error (not a 404) must not silently downgrade to a weaker checksum:
                // an interposed proxy that fails the SHA-512/SHA-256 fetches could otherwise force
                // verification down to SHA-1. A 404 — "this algorithm is not published" — is the
                // NotFoundException above and does fall through.
                throw new ModuleDownloadException(
                        fail(
                                service,
                                coordinate,
                                dir,
                                "the "
                                        + ext.toUpperCase(Locale.ROOT)
                                        + " checksum could not be retrieved ("
                                        + e.getMessage()
                                        + ") — refusing to fall back to a weaker checksum on a"
                                        + " transport error"));
            }
            String expected = parseChecksum(checksumBytes);
            String actual = digest(jarBytes, jdkAlgorithm(ext));
            if (!expected.equalsIgnoreCase(actual)) {
                throw new ModuleDownloadException(
                        fail(
                                service,
                                coordinate,
                                dir,
                                ext.toUpperCase(Locale.ROOT)
                                        + " checksum mismatch (expected "
                                        + expected
                                        + ", got "
                                        + actual
                                        + ") — the download may be corrupt or tampered with"));
            }
            return; // verified
        }
        throw new ModuleDownloadException(
                fail(
                        service,
                        coordinate,
                        dir,
                        "no checksum (.sha512/.sha256/.sha1) could be retrieved to verify the"
                                + " download"));
    }

    private byte[] fetch(String url) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 404) {
            throw new NotFoundException();
        }
        if (status / 100 != 2) {
            throw new IOException("HTTP " + status + " from " + url);
        }
        return response.body();
    }

    private String artifactUrl(String service, String version, String extension) {
        // io/github/cloudstub/cloudstub-<service>/<version>/cloudstub-<service>-<version>.<ext>
        String groupPath = GROUP.replace('.', '/');
        String artifact = "cloudstub-" + service;
        return baseUrl + "/" + groupPath + "/" + artifact + "/" + version + "/" + artifact + "-"
                + version + "." + extension;
    }

    // Maven checksum files hold the hex digest, optionally followed by whitespace and the filename.
    private static String parseChecksum(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        int space = indexOfWhitespace(text);
        return (space >= 0 ? text.substring(0, space) : text).trim();
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String digest(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK is missing required digest " + algorithm, e);
        }
    }

    private static String jdkAlgorithm(String extension) {
        return switch (extension) {
            case "sha512" -> "SHA-512";
            case "sha256" -> "SHA-256";
            case "sha1" -> "SHA-1";
            default -> throw new IllegalArgumentException("unsupported checksum: " + extension);
        };
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String fail(String service, String coordinate, Path dir, String reason) {
        return "could not provision service '"
                + service
                + "' from "
                + coordinate
                + " — "
                + reason
                + ".\n"
                + "            Place the jar manually in "
                + dir.toAbsolutePath()
                + " and restart, or disable auto-download with --no-download /"
                + " CLOUDSTUB_AUTO_DOWNLOAD=false.";
    }

    /** Signals an HTTP 404, distinguishing "not published" from other transport failures. */
    private static final class NotFoundException extends IOException {}
}

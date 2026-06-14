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
     * Returns whether a jar for {@code service} (any version) already exists in {@code dir}, so the
     * plugin directory acts as a cache and a present jar is never re-downloaded.
     *
     * @param dir plugin directory to inspect; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @return {@code true} if a matching jar is present
     */
    public static boolean isPresent(Path dir, String service) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        String exact = "cloudstub-" + service + ".jar";
        String versionedPrefix = "cloudstub-" + service + "-";
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString())
                    .anyMatch(
                            n ->
                                    n.endsWith(".jar")
                                            && (n.equals(exact) || n.startsWith(versionedPrefix)));
        } catch (IOException e) {
            return false;
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
            Files.write(tmp, jarBytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
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
                continue; // transient checksum-fetch error; fall through to a weaker algorithm
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

package io.cloudstub.core.download;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provisions a CloudStub service module jar from Maven Central into a plugin directory: fetch,
 * checksum-verify, then store.
 *
 * <p>Modules are self-contained ({@code compileOnly} on core, no runtime transitive dependencies),
 * so a single direct jar download per service is sufficient — this is not a transitive dependency
 * resolver. The network surface is one fixed Central base URL; no arbitrary repositories.
 *
 * <p>Layout is handled by {@link MavenModuleCoordinate}, HTTP by {@link HttpFetcher}, integrity by
 * {@link ChecksumVerifier}, and the plugin-directory cache by {@link ModuleCache}.
 *
 * <p>Instances are thread-safe.
 */
public final class ModuleDownloader {

    /** Canonical Maven Central artifact base. */
    public static final String CENTRAL_BASE_URL = "https://repo1.maven.org/maven2";

    /** Published Maven group shared by every CloudStub module. */
    public static final String GROUP = "io.github.cloudstub";

    private final String baseUrl;
    private final HttpFetcher fetcher;
    private final ChecksumVerifier checksumVerifier;

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
        this.fetcher = new HttpFetcher();
        this.checksumVerifier = new ChecksumVerifier(fetcher);
    }

    /**
     * @return the Maven coordinate string for a service at a version
     */
    public static String coordinate(String service, String version) {
        return new MavenModuleCoordinate(service, version).displayCoordinate();
    }

    /**
     * Returns whether the jar for {@code service} at {@code version} is present in {@code dir} —
     * either the exact versioned jar or an unversioned {@code cloudstub-<service>.jar}. A jar of a
     * different version is not a match.
     *
     * @param dir plugin directory to inspect; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @param version requested module version
     * @return {@code true} if the requested version (or an unversioned jar) is present
     */
    public static boolean isCached(Path dir, String service, String version) {
        return new ModuleCache(dir).contains(new MavenModuleCoordinate(service, version));
    }

    /**
     * Downloads the module jar for {@code service} at {@code version} into {@code dir}, verifying
     * its checksum before the file is made visible and pruning any other cached version.
     *
     * @param service service id (e.g. {@code sqs})
     * @param version module version to fetch
     * @param dir target plugin directory (created if absent)
     * @return the path the jar was written to
     * @throws ModuleDownloadException with an actionable message on any failure
     */
    public Path download(String service, String version, Path dir) {
        MavenModuleCoordinate coordinate = new MavenModuleCoordinate(service, version);
        coordinate.requireFileSystemSafe();

        byte[] jarBytes = fetchJar(coordinate, dir);
        checksumVerifier.verify(coordinate, baseUrl, jarBytes, dir);

        try {
            return new ModuleCache(dir).store(coordinate, jarBytes);
        } catch (IOException e) {
            throw ModuleDownloadException.provisioning(
                    coordinate, dir, "the jar could not be written (" + e + ")");
        }
    }

    private byte[] fetchJar(MavenModuleCoordinate coordinate, Path dir) {
        String jarUrl = coordinate.artifactUrl(baseUrl, "jar");
        try {
            return fetcher.get(jarUrl);
        } catch (HttpFetcher.NotFoundException e) {
            throw ModuleDownloadException.provisioning(
                    coordinate,
                    dir,
                    "no artifact was found at "
                            + jarUrl
                            + " (the service name may be wrong, or that version is not published)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ModuleDownloadException.provisioning(
                    coordinate, dir, "the download was interrupted");
        } catch (IOException e) {
            throw ModuleDownloadException.provisioning(
                    coordinate,
                    dir,
                    "the download failed (" + e.getMessage() + ") — check your network connection");
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

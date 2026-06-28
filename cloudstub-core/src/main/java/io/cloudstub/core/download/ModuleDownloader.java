package io.cloudstub.core.download;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern VERSION_ELEMENT = Pattern.compile("<version>([^<]+)</version>");

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
     * Returns a jar for {@code service} already present in {@code dir} at any version, regardless
     * of which version was requested. Intended as a fallback when a download cannot reach the
     * network: a previously provisioned (and at-download-time checksum-verified) jar is preferable
     * to failing to start.
     *
     * @param dir plugin directory to inspect; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @return the path of a cached jar for the service, or {@code null} if none is present
     */
    public static Path cachedJar(Path dir, String service) {
        return new ModuleCache(dir).locateAnyVersion(service);
    }

    /**
     * Returns the cached jar for {@code service} at exactly {@code version} (the versioned jar, or
     * an unversioned {@code cloudstub-<service>.jar}), or {@code null} if that version is not
     * cached. Unlike {@link #cachedJar(Path, String)}, a jar of a different version is not a match.
     *
     * @param dir plugin directory to inspect; may be {@code null}
     * @param service service id (e.g. {@code sqs})
     * @param version requested module version
     * @return the path of the cached jar for that exact version, or {@code null} if absent
     */
    public static Path cachedJar(Path dir, String service, String version) {
        return new ModuleCache(dir).locate(new MavenModuleCoordinate(service, version));
    }

    /**
     * Downloads the module jar for {@code service} into {@code dir}, verifying its checksum before
     * the file is made visible and pruning any other cached version.
     *
     * <p>The exact {@code version} is fetched when published. When it is not (the common case under
     * selective publishing, where an unchanged module is not re-released at every core version),
     * the highest published version that is less than or equal to {@code version} is resolved from
     * the artifact's {@code maven-metadata.xml} and fetched instead. A module already cached at the
     * resolved version is reused rather than re-downloaded.
     *
     * @param service service id (e.g. {@code sqs})
     * @param version requested module version (typically the running core version)
     * @param dir target plugin directory (created if absent)
     * @return the path the jar was written to, or the cached path when the resolved version is
     *     already present
     * @throws ModuleDownloadException with an actionable message on any failure
     */
    public Path download(String service, String version, Path dir) {
        MavenModuleCoordinate requested = new MavenModuleCoordinate(service, version);
        requested.requireFileSystemSafe();

        MavenModuleCoordinate resolved = requested;
        byte[] jarBytes = tryFetchJar(requested, dir);
        if (jarBytes == null) {
            resolved = resolveHighestPublishedAtMost(service, version, dir);
            Path cached = new ModuleCache(dir).locate(resolved);
            if (cached != null) {
                return cached;
            }
            jarBytes = fetchJar(resolved, dir);
        }
        checksumVerifier.verify(resolved, baseUrl, jarBytes, dir);

        try {
            return new ModuleCache(dir).store(resolved, jarBytes);
        } catch (IOException e) {
            throw ModuleDownloadException.provisioning(
                    resolved, dir, "the jar could not be written (" + e + ")");
        }
    }

    /** Fetches the jar, returning {@code null} on a 404 so the caller can fall back. */
    private byte[] tryFetchJar(MavenModuleCoordinate coordinate, Path dir) {
        try {
            return fetcher.get(coordinate.artifactUrl(baseUrl, "jar"));
        } catch (HttpFetcher.NotFoundException notPublished) {
            return null;
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

    /**
     * Resolves the highest published version of {@code service} that is less than or equal to
     * {@code requestedVersion}, from the artifact's {@code maven-metadata.xml}. Versions newer than
     * the requested one are excluded so an older core never loads a module built against a newer
     * one.
     */
    private MavenModuleCoordinate resolveHighestPublishedAtMost(
            String service, String requestedVersion, Path dir) {
        MavenModuleCoordinate requested = new MavenModuleCoordinate(service, requestedVersion);
        SemanticVersion ceiling = SemanticVersion.parseOrNull(requestedVersion);
        if (ceiling == null) {
            throw ModuleDownloadException.provisioning(
                    requested,
                    dir,
                    "that version is not published and is not a recognizable "
                            + "MAJOR.MINOR.PATCH[-prerelease] version to match an earlier release against");
        }

        String metadata = fetchMetadata(requested, dir);
        String best = null;
        SemanticVersion bestParsed = null;
        for (String candidate : parseVersions(metadata)) {
            SemanticVersion parsed = SemanticVersion.parseOrNull(candidate);
            if (parsed == null) {
                continue;
            }
            if (parsed.compareTo(ceiling) > 0) {
                continue;
            }
            if (bestParsed == null || parsed.compareTo(bestParsed) > 0) {
                best = candidate;
                bestParsed = parsed;
            }
        }
        if (best == null) {
            throw ModuleDownloadException.provisioning(
                    requested,
                    dir,
                    "that version is not published and no earlier published version was found");
        }
        MavenModuleCoordinate resolved = new MavenModuleCoordinate(service, best);
        resolved.requireFileSystemSafe();
        return resolved;
    }

    private String fetchMetadata(MavenModuleCoordinate coordinate, Path dir) {
        String metadataUrl = coordinate.metadataUrl(baseUrl);
        try {
            return new String(fetcher.get(metadataUrl), StandardCharsets.UTF_8);
        } catch (HttpFetcher.NotFoundException e) {
            throw ModuleDownloadException.provisioning(
                    coordinate,
                    dir,
                    "no artifact was found for that version, and no "
                            + metadataUrl
                            + " is published (the service name may be wrong)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ModuleDownloadException.provisioning(
                    coordinate, dir, "resolving an earlier version was interrupted");
        } catch (IOException e) {
            throw ModuleDownloadException.provisioning(
                    coordinate,
                    dir,
                    "resolving an earlier version failed ("
                            + e.getMessage()
                            + ") — check your network connection");
        }
    }

    private static List<String> parseVersions(String metadataXml) {
        List<String> versions = new ArrayList<>();
        Matcher matcher = VERSION_ELEMENT.matcher(metadataXml);
        while (matcher.find()) {
            versions.add(matcher.group(1).trim());
        }
        return versions;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

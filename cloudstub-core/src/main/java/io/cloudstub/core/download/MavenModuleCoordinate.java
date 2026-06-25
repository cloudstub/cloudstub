package io.cloudstub.core.download;

import java.util.regex.Pattern;

/** Maven coordinate and repository layout for a CloudStub service module at a version. */
final class MavenModuleCoordinate {

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final String service;
    private final String version;

    MavenModuleCoordinate(String service, String version) {
        this.service = service;
        this.version = version;
    }

    String service() {
        return service;
    }

    String displayCoordinate() {
        return ModuleDownloader.GROUP + ":" + artifactId() + ":" + version;
    }

    String jarFileName() {
        return artifactFileName("jar");
    }

    String unversionedJarFileName() {
        return artifactId() + ".jar";
    }

    String versionedJarPrefix() {
        return artifactId() + "-";
    }

    String artifactUrl(String baseUrl, String extension) {
        String groupPath = ModuleDownloader.GROUP.replace('.', '/');
        return baseUrl
                + "/"
                + groupPath
                + "/"
                + artifactId()
                + "/"
                + version
                + "/"
                + artifactFileName(extension);
    }

    /** URL of the artifact's {@code maven-metadata.xml} (version-independent). */
    String metadataUrl(String baseUrl) {
        String groupPath = ModuleDownloader.GROUP.replace('.', '/');
        return baseUrl + "/" + groupPath + "/" + artifactId() + "/maven-metadata.xml";
    }

    void requireFileSystemSafe() {
        requireSafePathSegment("service", service);
        requireSafePathSegment("version", version);
    }

    private String artifactFileName(String extension) {
        return artifactId() + "-" + version + "." + extension;
    }

    private String artifactId() {
        return "cloudstub-" + service;
    }

    private static void requireSafePathSegment(String label, String value) {
        if (value == null || !SAFE_PATH_SEGMENT.matcher(value).matches() || value.contains("..")) {
            throw new ModuleDownloadException(
                    "invalid module "
                            + label
                            + " '"
                            + value
                            + "': must match "
                            + SAFE_PATH_SEGMENT.pattern()
                            + " with no path separators or '..'");
        }
    }
}

package io.cloudstub.core.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** A plugin directory holding downloaded module jars, used as a per-version cache. */
final class ModuleCache {

    private final Path directory;

    ModuleCache(Path directory) {
        this.directory = directory;
    }

    boolean contains(MavenModuleCoordinate coordinate) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        String versioned = coordinate.jarFileName();
        String unversioned = coordinate.unversionedJarFileName();
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .anyMatch(name -> name.equals(versioned) || name.equals(unversioned));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @return the path of the cached jar for {@code coordinate} (preferring the versioned name over
     *     an unversioned one), or {@code null} if none is present
     */
    Path locate(MavenModuleCoordinate coordinate) {
        if (directory == null) {
            return null;
        }
        Path versioned = directory.resolve(coordinate.jarFileName());
        if (Files.exists(versioned)) {
            return versioned;
        }
        Path unversioned = directory.resolve(coordinate.unversionedJarFileName());
        return Files.exists(unversioned) ? unversioned : null;
    }

    /**
     * @return a cached jar of {@code service} at any version (an unversioned jar is preferred, then
     *     any versioned one — {@link #store} keeps a single versioned jar per service), or {@code
     *     null} if none is present
     */
    Path locateAnyVersion(String service) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        MavenModuleCoordinate any = new MavenModuleCoordinate(service, "0");
        Path unversioned = directory.resolve(any.unversionedJarFileName());
        if (Files.exists(unversioned)) {
            return unversioned;
        }
        String prefix = any.versionedJarPrefix();
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(
                            entry -> {
                                String name = entry.getFileName().toString();
                                return name.startsWith(prefix) && name.endsWith(".jar");
                            })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    Path store(MavenModuleCoordinate coordinate, byte[] jarBytes) throws IOException {
        Files.createDirectories(directory);
        String jarName = coordinate.jarFileName();
        Path target = directory.resolve(jarName);
        Path partialDownload = Files.createTempFile(directory, jarName, ".part");
        try {
            Files.write(partialDownload, jarBytes);
            Files.move(partialDownload, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteQuietly(partialDownload);
        }
        pruneOtherVersions(coordinate, jarName);
        return target;
    }

    private void pruneOtherVersions(MavenModuleCoordinate coordinate, String keepFileName) {
        String versionedPrefix = coordinate.versionedJarPrefix();
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();
                if (fileName.endsWith(".jar")
                        && fileName.startsWith(versionedPrefix)
                        && !fileName.equals(keepFileName)) {
                    deleteQuietly(entry);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}

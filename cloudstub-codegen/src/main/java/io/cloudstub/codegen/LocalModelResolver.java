package io.cloudstub.codegen;

import java.nio.file.Files;
import java.nio.file.Path;

class LocalModelResolver implements ModelResolver {

    private final Path path;

    LocalModelResolver(String arg) {
        this.path = Path.of(arg).toAbsolutePath().normalize();
    }

    @Override
    public Path resolve() {
        String filename = path.getFileName().toString();
        if (!filename.endsWith(".smithy") && !filename.endsWith(".json")) {
            throw new IllegalArgumentException(
                    "--model must be a .smithy or .json file, got: " + filename);
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Model path does not exist: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "--model must be a single file (.smithy or .json), not a directory: " + path);
        }
        return path;
    }
}

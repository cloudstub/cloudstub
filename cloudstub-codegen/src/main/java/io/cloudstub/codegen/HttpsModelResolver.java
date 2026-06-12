package io.cloudstub.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class HttpsModelResolver implements ModelResolver {

    private final URI uri;

    HttpsModelResolver(String url) {
        this.uri = URI.create(url);
    }

    @Override
    public Path resolve() throws IOException {
        String uriPath = uri.getPath();
        String filename = uriPath.substring(uriPath.lastIndexOf('/') + 1);
        validateExtension(filename);

        Path tmp = Files.createTempFile("cloudstub-model-", "-" + filename);
        tmp.toFile().deleteOnExit();

        System.out.println("Downloading model from: " + uri);
        try (InputStream in = uri.toURL().openStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private static void validateExtension(String filename) {
        if (!filename.endsWith(".smithy") && !filename.endsWith(".json")) {
            throw new IllegalArgumentException(
                    "URL must point to a .smithy or .json file, got: " + filename);
        }
    }
}

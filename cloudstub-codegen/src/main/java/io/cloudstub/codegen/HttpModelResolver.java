package io.cloudstub.codegen;

import java.nio.file.Path;

class HttpModelResolver implements ModelResolver {

    private final String url;

    HttpModelResolver(String url) {
        this.url = url;
    }

    @Override
    public Path resolve() {
        throw new IllegalArgumentException(
                "HTTP URLs are not supported — use https:// instead: " + url);
    }
}

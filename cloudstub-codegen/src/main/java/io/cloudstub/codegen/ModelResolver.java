package io.cloudstub.codegen;

import java.io.IOException;
import java.nio.file.Path;

public interface ModelResolver {

    Path resolve() throws IOException;

    static ModelResolver of(String arg) {
        if (arg.startsWith("https://")) return new HttpsModelResolver(arg);
        if (arg.startsWith("http://")) return new HttpModelResolver(arg);
        return new LocalModelResolver(arg);
    }
}

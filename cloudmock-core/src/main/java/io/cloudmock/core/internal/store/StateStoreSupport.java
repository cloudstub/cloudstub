package io.cloudmock.core.internal.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helpers shared by the {@link io.cloudmock.core.spi.StateStore} backends, so the in-memory and the
 * persistent stores agree on prefix semantics and on how a file is durably replaced rather than each
 * re-implementing them.
 */
final class StateStoreSupport {

    private StateStoreSupport() {}

    /** Returns the keys starting with {@code prefix}, sorted ascending. */
    static List<String> keysWithPrefix(Set<String> keys, String prefix) {
        List<String> matching = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                matching.add(key);
            }
        }
        matching.sort(Comparator.naturalOrder());
        return matching;
    }

    /** Removes every key starting with {@code prefix}; returns whether anything was removed. */
    static boolean removeKeysWithPrefix(Map<String, ?> data, String prefix) {
        return data.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Writes a temp file via {@code writer}, then atomically renames it over {@code target}. */
    static void atomicReplace(Path target, TmpFileWriter writer) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            writer.writeTo(tmp);
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // No-op after a successful move; cleans up a half-written temp file otherwise.
            Files.deleteIfExists(tmp);
        }
    }

    @FunctionalInterface
    interface TmpFileWriter {
        void writeTo(Path tmp) throws IOException;
    }
}

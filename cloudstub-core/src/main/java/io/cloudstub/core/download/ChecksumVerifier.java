package io.cloudstub.core.download;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Verifies downloaded jar bytes against the strongest published Maven checksum. */
final class ChecksumVerifier {

    private static final List<String> EXTENSIONS_STRONGEST_FIRST =
            List.of("sha512", "sha256", "sha1");

    private final HttpFetcher fetcher;

    ChecksumVerifier(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    void verify(MavenModuleCoordinate coordinate, String baseUrl, byte[] jarBytes, Path dir) {
        for (String extension : EXTENSIONS_STRONGEST_FIRST) {
            byte[] checksumBytes;
            try {
                checksumBytes = fetcher.get(coordinate.artifactUrl(baseUrl, "jar." + extension));
            } catch (HttpFetcher.NotFoundException checksumNotPublished) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ModuleDownloadException.provisioning(
                        coordinate, dir, "checksum retrieval was interrupted");
            } catch (IOException transportError) {
                throw ModuleDownloadException.provisioning(
                        coordinate,
                        dir,
                        "the "
                                + extension.toUpperCase(Locale.ROOT)
                                + " checksum could not be retrieved ("
                                + transportError.getMessage()
                                + ") — refusing to fall back to a weaker checksum on a transport"
                                + " error");
            }
            String expected = parseDigest(checksumBytes);
            String actual = hexDigest(jarBytes, jdkAlgorithm(extension));
            if (!expected.equalsIgnoreCase(actual)) {
                throw ModuleDownloadException.provisioning(
                        coordinate,
                        dir,
                        extension.toUpperCase(Locale.ROOT)
                                + " checksum mismatch (expected "
                                + expected
                                + ", got "
                                + actual
                                + ") — the download may be corrupt or tampered with");
            }
            return;
        }
        throw ModuleDownloadException.provisioning(
                coordinate,
                dir,
                "no checksum (.sha512/.sha256/.sha1) could be retrieved to verify the download");
    }

    private static String parseDigest(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        int endOfDigest = indexOfWhitespace(text);
        return (endOfDigest >= 0 ? text.substring(0, endOfDigest) : text).trim();
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String hexDigest(byte[] data, String algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
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
}

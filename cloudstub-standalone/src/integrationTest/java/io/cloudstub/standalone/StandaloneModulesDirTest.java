package io.cloudstub.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Verifies plugin-directory loading: modules are served only when their jar is present in the
 * modules directory, an explicit missing directory fails fast, and {@code --services} still filters
 * among the jars that are present.
 */
class StandaloneModulesDirTest {

    private static final int PORT_SQS_ONLY = 14600;
    private static final int PORT_EMPTY = 14610;
    private static final int PORT_FILTERED = 14620;

    private static Path allModulesDir() {
        String dir = System.getProperty("cloudstub.standalone.modules.dir");
        assertNotNull(dir, "cloudstub.standalone.modules.dir system property must be set");
        return Path.of(dir);
    }

    /** Copies only the SQS jar from the full modules directory into a fresh temp directory. */
    private static Path sqsOnlyDir() throws IOException {
        Path src = allModulesDir();
        Path tmp = Files.createTempDirectory("cloudstub-test-modules-sqs");
        try (Stream<Path> jars = Files.list(src)) {
            jars.filter(p -> p.getFileName().toString().contains("cloudstub-sqs"))
                    .forEach(
                            p -> {
                                try {
                                    Files.copy(p, tmp.resolve(p.getFileName()));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }
        return tmp;
    }

    private static void deleteDir(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void sqsServedWhenJarPresent() throws Exception {
        Path sqsDir = sqsOnlyDir();
        try (StandaloneProcess p =
                StandaloneProcess.startWithModulesDir(PORT_SQS_ONLY, sqsDir, "--services=sqs")) {
            try (SqsClient sqs =
                    SqsClient.builder()
                            .endpointOverride(URI.create("http://localhost:" + PORT_SQS_ONLY))
                            .region(Region.US_EAST_1)
                            .credentialsProvider(
                                    StaticCredentialsProvider.create(
                                            AwsBasicCredentials.create("test", "test")))
                            .build()) {
                assertTrue(sqs.listQueues().sdkHttpResponse().isSuccessful());
            }
        } finally {
            deleteDir(sqsDir);
        }
    }

    @Test
    void emptyModulesDirStartsWithNoServices() throws Exception {
        Path emptyDir = Files.createTempDirectory("cloudstub-test-modules-empty");
        try (StandaloneProcess p = StandaloneProcess.startWithModulesDir(PORT_EMPTY, emptyDir)) {
            assertTrue(
                    p.awaitOutput(l -> l.contains("Available services: (none)"), 5_000),
                    "Expected no available services with empty modules dir, got: " + p.output());
        } finally {
            deleteDir(emptyDir);
        }
    }

    @Test
    void servicesFilterAppliesAmongPresentJars() throws Exception {
        // All module jars present, but only sqs enabled via --services. SNS request must 404.
        try (StandaloneProcess p =
                StandaloneProcess.startWithModulesDir(
                        PORT_FILTERED, allModulesDir(), "--services=sqs")) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest snsRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + PORT_FILTERED + "/"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "Action=Publish&Message=hello"))
                            .build();
            HttpResponse<String> response =
                    client.send(snsRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    404, response.statusCode(), "SNS must not be served when --services=sqs only");
        }
    }

    @Test
    void explicitMissingDirFailsFast() throws Exception {
        String jarPath = System.getProperty("cloudstub.standalone.jar");
        assertNotNull(jarPath, "cloudstub.standalone.jar system property must be set");

        Process proc =
                new ProcessBuilder(
                                "java",
                                "-jar",
                                jarPath,
                                "--port=14699",
                                "--api-port=15699",
                                "--modules-dir=/nonexistent-path-that-cannot-exist-12345")
                        .redirectErrorStream(true)
                        .start();

        boolean exited = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(exited, "Process should exit quickly on missing explicit modules dir");
        assertTrue(proc.exitValue() != 0, "Process should exit non-zero");

        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(
                out.contains("does not exist") || out.contains("ERROR"),
                "Expected error message about missing directory, got: " + out);
    }
}

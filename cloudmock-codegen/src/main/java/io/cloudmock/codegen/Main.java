package io.cloudmock.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the CloudMock stub code generator.
 *
 * <pre>
 * java -jar cloudmock-codegen.jar --model &lt;path-or-url&gt; [--output &lt;dir&gt;] [--core-version &lt;version&gt;] [--validate] [--verbose]
 * </pre>
 *
 * <p>{@code --model} — path or HTTPS URL to a Smithy model file ({@code .smithy} IDL or {@code
 * .json} AST).
 *
 * <p>{@code --output} — directory to write the generated module into (default: {@code
 * ./<module-name>}).
 *
 * <p>{@code --core-version} — {@code cloudmock-core} version for the generated {@code build.gradle}
 * (default: {@value #DEFAULT_CORE_VERSION}).
 *
 * <p>{@code --validate} — validate the model and report what would be generated, without writing
 * any files. {@code --output} and {@code --core-version} are ignored in this mode.
 *
 * <p>{@code --verbose} — print each suppressed model validation event in full (default: count
 * only).
 */
public class Main {

    static final String DEFAULT_CORE_VERSION = "0.1.0-SNAPSHOT";

    public static void main(String[] args) throws IOException {
        String modelArg = null;
        Path outputDir = null;
        String coreVersion = DEFAULT_CORE_VERSION;
        boolean verbose = false;
        boolean validateOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model" -> modelArg = args[++i];
                case "--output" -> outputDir = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--core-version" -> coreVersion = args[++i];
                case "--validate" -> validateOnly = true;
                case "--verbose" -> verbose = true;
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    usage();
                    System.exit(1);
                }
            }
        }

        if (modelArg == null) {
            usage();
            System.exit(1);
        }

        Path modelPath;
        try {
            modelPath = ModelResolver.of(modelArg).resolve();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Loading model: " + modelArg);

        if (validateOnly) {
            ModelSummary summary;
            try {
                summary = new ModuleGenerator(verbose).validate(modelPath);
            } catch (Exception e) {
                System.err.println("Validation failed: " + e.getMessage());
                System.exit(1);
                return;
            }
            System.out.println(
                    "Model is valid: service '"
                            + summary.serviceId()
                            + "' → module '"
                            + summary.moduleName()
                            + "' ("
                            + summary.protocol()
                            + ", "
                            + summary.operations().size()
                            + " operation(s))");
            summary.operations().forEach(op -> System.out.println("  - " + op));
            System.out.println("\nNo files written (--validate).");
            return;
        }

        GenerationResult result;

        try {
            result = new ModuleGenerator(verbose).generate(modelPath, coreVersion);
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        Path target = outputDir != null ? outputDir : Path.of(result.moduleName());
        System.out.println(
                "Generating module '"
                        + result.moduleName()
                        + "' (service: "
                        + result.serviceId()
                        + ") into "
                        + target.toAbsolutePath());

        for (GeneratedFile file : result.files()) {
            Path dest = target.resolve(file.relativePath());
            Files.createDirectories(dest.getParent());
            Files.writeString(dest, file.content());
            System.out.println("  wrote " + target.relativize(dest));
        }

        System.out.println("\nDone. Next steps:");
        System.out.println("  1. Add 'include \"" + result.moduleName() + "\"' to settings.gradle");
        System.out.println(
                "  2. Publish cloudmock-core to your local Maven repo: gradle publishToMavenLocal");
        System.out.println(
                "  3. Review and update each .hbs template in src/main/resources/templates/");
        System.out.println(
                "  4. Fix the X-Amz-Target prefix (JSON services) or Action values (XML)");
        System.out.println("  5. Complete the test class with real SDK client calls");
        System.out.println("  6. Run: gradle :" + result.moduleName() + ":test");
    }

    private static void usage() {
        System.err.println(
                "Usage: java -jar cloudmock-codegen.jar --model <path-or-url> [--output <dir>] [--core-version <version>] [--validate] [--verbose]");
        System.err.println(
                "  --model        <path-or-url>  single Smithy model file (.smithy IDL or .json AST), local path or https:// URL");
        System.err.println(
                "  --output       <dir>          output directory (default: ./<module-name>)");
        System.err.println(
                "  --core-version <version>      cloudmock-core version (default: "
                        + DEFAULT_CORE_VERSION
                        + ")");
        System.err.println(
                "  --validate                    validate the model and report what would be generated, without writing files");
        System.err.println(
                "  --verbose                     print each suppressed model validation event in full");
    }
}

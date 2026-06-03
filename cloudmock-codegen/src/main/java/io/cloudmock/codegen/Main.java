package io.cloudmock.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the CloudMock stub code generator.
 *
 * <pre>
 * java -jar cloudmock-codegen.jar --model &lt;path&gt; [--output &lt;dir&gt;]
 * </pre>
 *
 * <p>{@code --model} — path to a {@code .smithy} file or a directory of {@code .smithy} files.
 * <p>{@code --output} — directory to write the generated module into (default: {@code ./<module-name>}).
 */
public class Main {

    public static void main(String[] args) throws IOException {
        Path modelPath = null;
        Path outputDir = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model"  -> modelPath = Path.of(args[++i]);
                case "--output" -> outputDir = Path.of(args[++i]);
                default         -> {
                    System.err.println("Unknown argument: " + args[i]);
                    usage();
                    System.exit(1);
                }
            }
        }

        if (modelPath == null) {
            usage();
            System.exit(1);
        }

        if (!Files.exists(modelPath)) {
            System.err.println("Model path does not exist: " + modelPath);
            System.exit(1);
        }

        System.out.println("Loading model: " + modelPath);
        GenerationResult result;
        try {
            result = new ModuleGenerator().generate(modelPath);
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        Path target = outputDir != null ? outputDir : Path.of(result.moduleName());
        System.out.println("Generating module '" + result.moduleName()
                + "' (service: " + result.serviceId() + ") into " + target.toAbsolutePath());

        for (GeneratedFile file : result.files()) {
            Path dest = target.resolve(file.relativePath());
            Files.createDirectories(dest.getParent());
            Files.writeString(dest, file.content());
            System.out.println("  wrote " + target.relativize(dest));
        }

        System.out.println("\nDone. Next steps:");
        System.out.println("  1. Add 'include \"" + result.moduleName() + "\"' to settings.gradle");
        System.out.println("  2. Review and update each template constant in the service class");
        System.out.println("  3. Fix the X-Amz-Target prefix (JSON services) or Action values (XML)");
        System.out.println("  4. Complete the test class with real SDK client calls");
        System.out.println("  5. Run: gradle :" + result.moduleName() + ":test");
    }

    private static void usage() {
        System.err.println("Usage: java -jar cloudmock-codegen.jar --model <path> [--output <dir>]");
        System.err.println("  --model  <path>  .smithy file or directory of .smithy files");
        System.err.println("  --output <dir>   output directory (default: ./<module-name>)");
    }
}

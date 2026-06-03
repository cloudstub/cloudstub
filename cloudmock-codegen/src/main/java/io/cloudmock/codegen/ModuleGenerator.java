package io.cloudmock.codegen;

import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.validation.ValidatedResult;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a complete, compilable {@code cloudmock-*} module skeleton from a Smithy service model.
 *
 * <p>All generated response templates are intentional placeholders — they compile and register
 * correctly but return minimal responses. Human review is always required before a generated
 * module is production-ready.
 */
public class ModuleGenerator {

    public GenerationResult generate(Path modelPath) {
        Model model = loadModel(modelPath);

        ServiceShape service = model.shapes(ServiceShape.class)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No service shape found in: " + modelPath));

        String serviceId  = deriveServiceId(service);
        String className  = deriveClassName(service);
        String pkg        = "io.cloudmock." + serviceId;
        String moduleName = "cloudmock-" + serviceId;
        Protocol protocol = ProtocolDetector.detect(service);

        List<OperationShape> operations = service.getAllOperations().stream()
                .map(id -> model.expectShape(id, OperationShape.class))
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .collect(Collectors.toList());

        List<GeneratedFile> files = new ArrayList<>();
        files.add(buildGradle(moduleName, serviceId));
        files.add(serviceClass(model, service, operations, protocol, pkg, className));
        files.add(serviceLoaderFile(pkg, className));
        files.add(testClass(operations, pkg, className));

        return new GenerationResult(serviceId, moduleName, files);
    }

    // ── model loading ─────────────────────────────────────────────────────────

    private Model loadModel(Path modelPath) {
        ValidatedResult<Model> result = Model.assembler()
                .discoverModels()   // loads AWS trait definitions from smithy-aws-traits on classpath
                .addImport(modelPath)
                .assemble();
        result.getValidationEvents().stream()
                .filter(e -> e.getSeverity().ordinal() >= 3) // DANGER or ERROR
                .forEach(e -> System.err.println("  [" + e.getSeverity() + "] " + e.getMessage()));
        List<ValidationEvent> fatalErrors = result.getValidationEvents().stream()
                .filter(e -> e.getSeverity().ordinal() >= 4) // ERROR only
                .collect(Collectors.toList());
        if (!fatalErrors.isEmpty()) {
            throw new IllegalArgumentException(
                    fatalErrors.size() + " model error(s) — fix them before generating.");
        }
        return result.getResult().orElseThrow(
                () -> new IllegalArgumentException("Model produced no output."));
    }

    // ── name derivation ───────────────────────────────────────────────────────

    private String deriveServiceId(ServiceShape service) {
        return service.getTrait(ServiceTrait.class)
                .map(t -> t.getSdkId().toLowerCase().replaceAll("[^a-z0-9]", ""))
                .orElseGet(() -> service.getId().getName().toLowerCase());
    }

    private String deriveClassName(ServiceShape service) {
        String raw = service.getTrait(ServiceTrait.class)
                .map(ServiceTrait::getSdkId)
                .orElseGet(() -> service.getId().getName());
        return Arrays.stream(raw.split("[\\s\\-_]+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining());
    }

    // ── file generators ───────────────────────────────────────────────────────

    private GeneratedFile buildGradle(String moduleName, String serviceId) {
        String content = """
                dependencies {
                    compileOnly project(':cloudmock-core')
                    testImplementation project(':cloudmock-core')
                    // TODO: replace with correct AWS SDK library alias from libs.versions.toml
                    // testImplementation libs.aws.%s
                }
                """.formatted(serviceId);
        return new GeneratedFile("build.gradle", content);
    }

    private GeneratedFile serviceClass(Model model, ServiceShape service,
            List<OperationShape> operations, Protocol protocol,
            String pkg, String className) {

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import io.cloudmock.core.spi.CloudMockService;\n");
        sb.append("import io.cloudmock.core.spi.StubRegistrar;\n");
        if (protocol == Protocol.REST) {
            sb.append("import io.cloudmock.core.spi.HttpMethod;\n");
        }
        sb.append("\n");
        sb.append("""
                /**
                 * CloudMock service module for %s.
                 *
                 * <p><strong>GENERATED — HUMAN REVIEW REQUIRED.</strong>
                 * Response templates are minimal placeholders. Replace each template constant
                 * with a well-formed Handlebars response that the AWS SDK can parse without error.
                 * See existing modules (cloudmock-sqs, cloudmock-secretsmanager) for examples.
                 */
                """.formatted(className));
        sb.append("public class CloudMock").append(className)
                .append("Service implements CloudMockService {\n\n");

        String serviceIdVal = deriveServiceId(service);
        sb.append("    private static final String SERVICE_ID = \"").append(serviceIdVal).append("\";\n");

        if (protocol == Protocol.JSON_TARGET) {
            sb.append("    // TODO: verify X-Amz-Target prefix — common formats:\n");
            sb.append("    //   \"Amazon<Name>.\"  e.g. \"AmazonSQS.\"\n");
            sb.append("    //   \"<name>.\"         e.g. \"secretsmanager.\"\n");
            sb.append("    //   \"<Name>_<Version>.\"  e.g. \"DynamoDB_20120810.\"\n");
            sb.append("    private static final String TARGET_PREFIX = \"")
                    .append(className).append(".\";\n");
        }
        sb.append("\n");

        for (OperationShape op : operations) {
            String constName = toScreamingSnake(op.getId().getName());
            sb.append(templateComment(model, op));
            sb.append("    private static final String ").append(constName)
                    .append(" = \"").append(templateBody(model, op, protocol)).append("\";\n\n");
        }

        sb.append("    @Override\n");
        sb.append("    public String serviceId() {\n");
        sb.append("        return SERVICE_ID;\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void register(StubRegistrar registrar) {\n");
        for (OperationShape op : operations) {
            String opName    = op.getId().getName();
            String constName = toScreamingSnake(opName);
            switch (protocol) {
                case JSON_TARGET -> sb.append(
                        "        registrar.registerJsonTargetStub(TARGET_PREFIX + \"")
                        .append(opName).append("\", ").append(constName).append(");\n");
                case FORM_URL -> sb.append(
                        "        registrar.registerXmlFormStub(\"")
                        .append(opName).append("\", ").append(constName).append(");\n");
                case REST -> {
                    String[] mp = httpMethodAndPath(op);
                    sb.append("        registrar.registerRestStub(HttpMethod.")
                            .append(mp[0]).append(", \"").append(mp[1]).append("\", ")
                            .append(constName).append(");\n");
                }
            }
        }
        sb.append("    }\n}\n");

        String path = "src/main/java/" + pkg.replace('.', '/') + "/CloudMock" + className + "Service.java";
        return new GeneratedFile(path, sb.toString());
    }

    private GeneratedFile serviceLoaderFile(String pkg, String className) {
        return new GeneratedFile(
                "src/main/resources/META-INF/services/io.cloudmock.core.spi.CloudMockService",
                pkg + ".CloudMock" + className + "Service\n");
    }

    private GeneratedFile testClass(List<OperationShape> operations, String pkg, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import io.cloudmock.core.CloudMock;\n");
        sb.append("import org.junit.jupiter.api.AfterAll;\n");
        sb.append("import org.junit.jupiter.api.BeforeAll;\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;\n");
        sb.append("import software.amazon.awssdk.regions.Region;\n");
        sb.append("// TODO: import the correct AWS SDK client and model classes\n");
        sb.append("import java.net.URI;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");

        sb.append("class CloudMock").append(className).append("ServiceTest {\n\n");
        sb.append("    static CloudMock cloudMock;\n");
        sb.append("    // TODO: declare SDK client field, e.g.:\n");
        sb.append("    // static ").append(className).append("Client client;\n\n");

        sb.append("    @BeforeAll\n");
        sb.append("    static void start() {\n");
        sb.append("        cloudMock = new CloudMock().withService(new CloudMock")
                .append(className).append("Service());\n");
        sb.append("        cloudMock.start();\n");
        sb.append("        // TODO: build SDK client, e.g.:\n");
        sb.append("        // client = ").append(className).append("Client.builder()\n");
        sb.append("        //         .endpointOverride(URI.create(\"http://localhost:\" + cloudMock.port()))\n");
        sb.append("        //         .credentialsProvider(AnonymousCredentialsProvider.create())\n");
        sb.append("        //         .region(Region.US_EAST_1)\n");
        sb.append("        //         .build();\n");
        sb.append("    }\n\n");

        sb.append("    @AfterAll\n");
        sb.append("    static void stop() {\n");
        sb.append("        // client.close();\n");
        sb.append("        cloudMock.stop();\n");
        sb.append("    }\n\n");

        for (OperationShape op : operations) {
            String opName     = op.getId().getName();
            String methodName = Character.toLowerCase(opName.charAt(0)) + opName.substring(1);
            sb.append("    @Test\n");
            sb.append("    void ").append(methodName).append("() {\n");
            sb.append("        // TODO: call client.").append(methodName).append("() and assert\n");
            sb.append("        fail(\"Not implemented\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");

        String path = "src/test/java/" + pkg.replace('.', '/') + "/CloudMock" + className + "ServiceTest.java";
        return new GeneratedFile(path, sb.toString());
    }

    // ── template helpers ──────────────────────────────────────────────────────

    private String templateComment(Model model, OperationShape op) {
        StringBuilder sb = new StringBuilder("    // REVIEW REQUIRED");
        op.getOutput().ifPresent(outputId -> {
            Shape output = model.expectShape(outputId);
            sb.append(" — output: ").append(output.getId().getName());
            if (output instanceof StructureShape struct && !struct.getAllMembers().isEmpty()) {
                String members = struct.getAllMembers().entrySet().stream()
                        .map(e -> e.getKey() + ": "
                                + model.expectShape(e.getValue().getTarget()).getType())
                        .collect(Collectors.joining(", "));
                sb.append(" [").append(members).append("]");
            }
        });
        if (op.getOutput().isEmpty()) {
            sb.append(" — no output shape (returns empty response)");
        }
        return sb.append("\n").toString();
    }

    private String templateBody(Model model, OperationShape op, Protocol protocol) {
        if (protocol == Protocol.FORM_URL) {
            String name = op.getId().getName();
            return "<" + name + "Response><ResponseMetadata>"
                    + "<RequestId>{{randomValue type='UUID'}}</RequestId>"
                    + "</ResponseMetadata></" + name + "Response>";
        }
        return op.getOutput()
                .map(outId -> model.expectShape(outId))
                .filter(s -> s instanceof StructureShape)
                .map(s -> (StructureShape) s)
                .filter(s -> !s.getAllMembers().isEmpty())
                .map(s -> buildJsonTemplate(model, s))
                .orElse("{}");
    }

    private String buildJsonTemplate(Model model, StructureShape output) {
        String fields = output.getAllMembers().entrySet().stream()
                .limit(8)
                .map(e -> {
                    String name   = e.getKey();
                    Shape  target = model.expectShape(e.getValue().getTarget());
                    return "\\\"" + name + "\\\":" + jsonPlaceholder(target);
                })
                .collect(Collectors.joining(","));
        return "{" + fields + "}";
    }

    private String jsonPlaceholder(Shape shape) {
        return switch (shape.getType()) {
            case STRING  -> "\\\"{{randomValue type='UUID'}}\\\"";
            case INTEGER, LONG, SHORT, BYTE, BIG_INTEGER -> "0";
            case FLOAT, DOUBLE, BIG_DECIMAL             -> "0.0";
            case BOOLEAN                                 -> "false";
            case LIST, SET                               -> "[]";
            default                                      -> "{}";
        };
    }

    private String[] httpMethodAndPath(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .map(h -> new String[]{
                        h.getMethod().toUpperCase(),
                        h.getUri().toString().replaceAll("\\{[^}+][^}]*}", "[^/]+")
                                              .replaceAll("\\{\\+[^}]*}", ".+")
                })
                .orElse(new String[]{"POST", "/.*"});
    }

    private String toScreamingSnake(String camelCase) {
        return camelCase
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase();
    }
}

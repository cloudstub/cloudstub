package io.cloudmock.codegen;

import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.validation.ValidatedResult;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a complete, compilable {@code cloudmock-*} module skeleton from a Smithy service model.
 *
 * <p>All generated response templates are intentional placeholders — they compile and register
 * correctly but return minimal responses. Human review is always required before a generated
 * module is production-ready.
 */
public class ModuleGenerator {

    private final boolean verbose;

    public ModuleGenerator() {
        this(false);
    }

    /** @param verbose when true, print each suppressed model validation event in full. */
    public ModuleGenerator(boolean verbose) {
        this.verbose = verbose;
    }

    public GenerationResult generate(Path modelPath, String coreVersion) {
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

        // Hard gate: a service with no operations means the wrong file was passed, or the model failed
        // to assemble structurally (validation is disabled, so this is our main safety net). Generating
        // an empty module would silently produce a useless stub, so fail loudly instead.
        if (operations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Service '" + service.getId() + "' resolved 0 operations — "
                    + "is this the right model file?");
        }

        List<GeneratedFile> files = new ArrayList<>();
        files.add(buildGradle(serviceId, coreVersion));
        files.add(serviceClass(service, operations, protocol, pkg, className));
        files.add(serviceLoaderFile(pkg, className));
        files.add(testClass(operations, pkg, className));
        for (OperationShape op : operations) {
            files.add(templateFile(model, op, protocol));
        }
        boolean isXml = (protocol == Protocol.FORM_URL || protocol == Protocol.REST_XML);
        for (OperationShape op : operations) {
            files.add(builderClass(model, op, protocol, pkg, isXml));
        }
        // One shared serialisation helper per module, rather than copying the helpers into every builder.
        files.add(responseSupportClass(pkg, isXml));

        return new GenerationResult(serviceId, moduleName, files);
    }

    private Model loadModel(Path modelPath) {
        // allowUnknownTraits: real-world AWS models use traits (smithy.rules, smithy.waiters)
        //   not bundled with smithy-aws-traits — ignore them rather than failing.
        // disableValidation: enum constraints in aws-traits (e.g. ChecksumAlgorithm) lag behind
        //   the actual models; a code-gen tool needs structure, not strict trait validation.
        ValidatedResult<Model> result = Model.assembler()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .discoverModels()
                .addImport(modelPath)
                .disableValidation()
                .assemble();

        // Validation is disabled (above), but the assembler still collects events. Surface the
        // notable ones (DANGER + ERROR) so a malformed user model isn't silently accepted. We print
        // a one-line count by default and the full messages only under --verbose, because real AWS
        // models routinely trip lagging enum/trait constraints that are noise, not user errors.
        List<ValidationEvent> notable = result.getValidationEvents().stream()
                .filter(e -> e.getSeverity().ordinal() >= 3)
                .toList();
        if (!notable.isEmpty()) {
            System.err.println("  " + notable.size() + " model validation note(s) suppressed"
                    + (verbose ? ":" : " — re-run with --verbose for details"));
            if (verbose) {
                notable.forEach(e -> System.err.println("    [" + e.getSeverity() + "] " + e.getMessage()));
            }
        }
        return result.getResult().orElseThrow(
                () -> new IllegalArgumentException("Model produced no output."));
    }

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

    private GeneratedFile buildGradle(String serviceId, String coreVersion) {
        String content = """
                dependencies {
                    compileOnly 'io.cloudmock:cloudmock-core:%s'
                    testImplementation 'io.cloudmock:cloudmock-core:%s'
                    // TODO: add the AWS SDK v2 client dependency, e.g.:
                    // testImplementation 'software.amazon.awssdk:%s:VERSION'
                }
                """.formatted(coreVersion, coreVersion, serviceId);
        return new GeneratedFile("build.gradle", content);
    }

    private GeneratedFile serviceClass(ServiceShape service,
            List<OperationShape> operations, Protocol protocol,
            String pkg, String className) {

        String simpleClassName = "CloudMock" + className + "Service";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import io.cloudmock.core.spi.CloudMockContext;\n");
        sb.append("import io.cloudmock.core.spi.CloudMockService;\n");
        sb.append("import io.cloudmock.core.spi.StubRegistrar;\n");
        if (protocol.isRest()) {
            sb.append("import io.cloudmock.core.spi.HttpMethod;\n");
        }
        sb.append("import java.io.IOException;\n");
        sb.append("import java.io.InputStream;\n");
        sb.append("import java.io.UncheckedIOException;\n");
        sb.append("\n");
        sb.append("""
                /**
                 * CloudMock service module for %s.
                 *
                 * <p><strong>GENERATED — HUMAN REVIEW REQUIRED.</strong>
                 * Response templates are minimal placeholders in {@code src/main/resources/templates/}.
                 * Replace each {@code .hbs} file with a well-formed Handlebars response that the AWS SDK
                 * can parse without error. See existing modules (cloudmock-sqs, cloudmock-secretsmanager)
                 * for examples.
                 */
                """.formatted(className));
        sb.append("public class ").append(simpleClassName).append(" implements CloudMockService {\n\n");

        sb.append("    private static final String SERVICE_ID = \"")
                .append(deriveServiceId(service)).append("\";\n");

        if (protocol == Protocol.JSON_TARGET) {
            sb.append("    // TODO: verify X-Amz-Target prefix — common formats:\n");
            sb.append("    //   \"Amazon<Name>.\"  e.g. \"AmazonSQS.\"\n");
            sb.append("    //   \"<name>.\"         e.g. \"secretsmanager.\"\n");
            sb.append("    //   \"<Name>_<Version>.\"  e.g. \"DynamoDB_20120810.\"\n");
            sb.append("    private static final String TARGET_PREFIX = \"")
                    .append(className).append(".\";\n");
        }
        sb.append("\n");

        sb.append("    @Override\n");
        sb.append("    public String serviceId() {\n");
        sb.append("        return SERVICE_ID;\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void register(CloudMockContext context) {\n");
        sb.append("        StubRegistrar registrar = context.registrar();\n");
        for (OperationShape op : operations) {
            String opName = op.getId().getName();
            switch (protocol) {
                case JSON_TARGET -> sb.append(
                        "        registrar.registerJsonTargetStub(TARGET_PREFIX + \"")
                        .append(opName).append("\", loadTemplate(\"").append(opName).append("\"));\n");
                case FORM_URL -> sb.append(
                        "        registrar.registerXmlFormStub(\"")
                        .append(opName).append("\", loadTemplate(\"").append(opName).append("\"));\n");
                case REST_JSON, REST_XML -> {
                    String[] mp = httpMethodAndPath(op);
                    sb.append("        registrar.registerRestStub(HttpMethod.")
                            .append(mp[0]).append(", \"").append(mp[1]).append("\", loadTemplate(\"")
                            .append(opName).append("\"));\n");
                }
            }
        }
        sb.append("    }\n\n");

        sb.append("    private static String loadTemplate(String name) {\n");
        sb.append("        String path = \"/templates/\" + name + \".hbs\";\n");
        sb.append("        try (InputStream in = ").append(simpleClassName)
                .append(".class.getResourceAsStream(path)) {\n");
        sb.append("            if (in == null)\n");
        sb.append("                throw new IllegalStateException(\"Template not found: \" + path);\n");
        sb.append("            return new String(in.readAllBytes(),\n");
        sb.append("                    java.nio.charset.StandardCharsets.UTF_8).trim();\n");
        sb.append("        } catch (IOException e) {\n");
        sb.append("            throw new UncheckedIOException(e);\n");
        sb.append("        }\n");
        sb.append("    }\n}\n");

        String path = "src/main/java/" + pkg.replace('.', '/') + "/" + simpleClassName + ".java";
        return new GeneratedFile(path, sb.toString());
    }

    private GeneratedFile serviceLoaderFile(String pkg, String className) {
        return new GeneratedFile(
                "src/main/resources/META-INF/services/io.cloudmock.core.spi.CloudMockService",
                pkg + ".CloudMock" + className + "Service\n");
    }

    private GeneratedFile templateFile(Model model, OperationShape op, Protocol protocol) {
        String name = op.getId().getName();
        StringBuilder sb = new StringBuilder();

        op.getOutput().ifPresent(outputId -> {
            Shape output = model.expectShape(outputId);
            sb.append("{{! REVIEW REQUIRED — output: ").append(output.getId().getName());
            if (output instanceof StructureShape struct && !struct.getAllMembers().isEmpty()) {
                String members = struct.getAllMembers().entrySet().stream()
                        .map(e -> e.getKey() + ": "
                                + model.expectShape(e.getValue().getTarget()).getType())
                        .collect(Collectors.joining(", "));
                sb.append(" [").append(members).append("]");
            }
            sb.append(" }}\n");
        });
        if (op.getOutput().isEmpty()) {
            sb.append("{{! REVIEW REQUIRED — no output shape (returns empty response) }}\n");
        }

        sb.append(templateBody(model, op, protocol));

        return new GeneratedFile("src/main/resources/templates/" + name + ".hbs", sb.toString());
    }

    private GeneratedFile builderClass(Model model, OperationShape op, Protocol protocol, String pkg, boolean isXml) {
        String opName = op.getId().getName();
        String className = opName + "ResponseBuilder";

        // Validation is disabled during model assembly (see loadModel), so an operation's output
        // may resolve to something other than a structure in a malformed model. Degrade gracefully
        // to an empty builder rather than throwing, mirroring the defensive handling in templateBody.
        List<MemberShape> requiredMembers = new ArrayList<>();
        List<MemberShape> optionalMembers = new ArrayList<>();
        op.getOutput()
                .map(model::expectShape)
                .filter(s -> s instanceof StructureShape)
                .map(s -> (StructureShape) s)
                .ifPresent(output -> {
                    for (var member : output.getAllMembers().values()) {
                        if (member.hasTrait(RequiredTrait.class)) {
                            requiredMembers.add(member);
                        } else {
                            optionalMembers.add(member);
                        }
                    }
                });

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".response;\n\n");
        sb.append("/**\n");
        sb.append(" * GENERATED — builds a ").append(isXml ? "XML" : "JSON")
          .append(" response body for {@code ").append(opName).append("}.\n");
        sb.append(" *\n");
        sb.append(" * <p>Populate fields from your state store, then call {@link #build()} to get the\n");
        sb.append(" * wire-format string to return from a stub.\n");
        if (!requiredMembers.isEmpty()) {
            sb.append(" * Required fields are enforced via the constructor.\n");
        }
        sb.append(" */\n");
        sb.append("public final class ").append(className).append(" {\n\n");
        sb.append("    private final java.util.Map<String, Object> _fields = new java.util.LinkedHashMap<>();\n\n");

        if (!requiredMembers.isEmpty()) {
            String params = requiredMembers.stream()
                    .map(m -> toJavaType(model, m) + " " + sanitizeIdentifier(m.getMemberName()))
                    .collect(Collectors.joining(", "));
            sb.append("    public ").append(className).append("(").append(params).append(") {\n");
            for (var m : requiredMembers) {
                sb.append("        _fields.put(\"").append(wireName(m, isXml)).append("\", ")
                  .append(sanitizeIdentifier(m.getMemberName())).append(");\n");
            }
            sb.append("    }\n\n");
        } else {
            sb.append("    public ").append(className).append("() {}\n\n");
        }

        for (var m : optionalMembers) {
            String javaType = toJavaType(model, m);
            sb.append("    public ").append(className).append(" ").append(sanitizeIdentifier(m.getMemberName()))
              .append("(").append(javaType).append(" value) {\n");
            sb.append("        _fields.put(\"").append(wireName(m, isXml)).append("\", value);\n");
            sb.append("        return this;\n");
            sb.append("    }\n\n");
        }

        sb.append("    /** @return ").append(isXml ? "XML" : "JSON")
          .append(" wire-format string for the {@code ").append(opName).append("} response. */\n");
        sb.append("    public String build() {\n");
        if (protocol == Protocol.FORM_URL) {
            sb.append("        return \"<").append(opName).append("Response>\"\n");
            sb.append("                + ResponseSupport.toXml(\"").append(opName).append("Result\", _fields)\n");
            sb.append("                + \"<ResponseMetadata><RequestId>\" + java.util.UUID.randomUUID() + \"</RequestId></ResponseMetadata>\"\n");
            sb.append("                + \"</").append(opName).append("Response>\";\n");
        } else if (protocol == Protocol.REST_XML) {
            String xmlRoot = op.getOutput()
                    .map(id -> model.expectShape(id).getId().getName())
                    .orElse(opName + "Output");
            sb.append("        return ResponseSupport.toXml(\"").append(xmlRoot).append("\", _fields);\n");
        } else {
            sb.append("        return ResponseSupport.toJson(_fields);\n");
        }
        sb.append("    }\n");

        sb.append("}\n");

        String path = "src/main/java/" + pkg.replace('.', '/') + "/response/" + className + ".java";
        return new GeneratedFile(path, sb.toString());
    }

    /**
     * Maps a Smithy output member to the Java type exposed on the generated builder. Numeric, temporal,
     * and enum shapes get precise types; nested aggregates (structure/union/map/list) are surfaced as
     * generic collections that the shared serialiser understands.
     */
    private String toJavaType(Model model, MemberShape member) {
        Shape target = model.expectShape(member.getTarget());
        return switch (target.getType()) {
            case STRING, ENUM, BLOB -> "String";
            case BOOLEAN            -> "Boolean";
            case BYTE               -> "Byte";
            case SHORT              -> "Short";
            case INTEGER, INT_ENUM  -> "Integer";
            case LONG               -> "Long";
            case FLOAT              -> "Float";
            case DOUBLE             -> "Double";
            case BIG_INTEGER        -> "java.math.BigInteger";
            case BIG_DECIMAL        -> "java.math.BigDecimal";
            case TIMESTAMP          -> "java.time.Instant";
            case DOCUMENT           -> "Object";
            case LIST, SET          -> "java.util.List<Object>";
            case MAP, STRUCTURE, UNION -> "java.util.Map<String, Object>";
            default                 -> "java.util.Map<String, Object>";
        };
    }

    /**
     * The serialised wire name for a member: {@code @jsonName}/{@code @xmlName} override the member
     * name when present, matching how the real AWS SDK marshals the field. The member name itself is
     * still used for the Java-side builder identifier (see {@link #sanitizeIdentifier(String)}).
     */
    private String wireName(MemberShape member, boolean isXml) {
        if (isXml) {
            return member.getTrait(XmlNameTrait.class).map(XmlNameTrait::getValue).orElse(member.getMemberName());
        }
        return member.getTrait(JsonNameTrait.class).map(JsonNameTrait::getValue).orElse(member.getMemberName());
    }

    /** Java reserved words and literals that cannot be used verbatim as identifiers. */
    private static final Set<String> JAVA_RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "_");

    /**
     * Turns a Smithy member name into a legal Java identifier. Smithy member names already match
     * {@code [a-zA-Z_][a-zA-Z0-9_]*}, so the only collision with the Java grammar is reserved words;
     * those get a trailing underscore (e.g. {@code default} → {@code default_}). The original member
     * name is still used as the serialised wire key, so this rename never affects the response body.
     */
    private static String sanitizeIdentifier(String memberName) {
        return JAVA_RESERVED.contains(memberName) ? memberName + "_" : memberName;
    }

    private GeneratedFile responseSupportClass(String pkg, boolean isXml) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".response;\n\n");
        sb.append("/**\n");
        sb.append(" * GENERATED — shared ").append(isXml ? "XML" : "JSON")
          .append(" serialisation helpers for this module's response builders.\n");
        sb.append(" */\n");
        sb.append("final class ResponseSupport {\n\n");
        sb.append("    private ResponseSupport() {}\n\n");
        if (isXml) {
            appendXmlHelpers(sb);
        } else {
            appendJsonHelpers(sb);
        }
        sb.append("}\n");

        String path = "src/main/java/" + pkg.replace('.', '/') + "/response/ResponseSupport.java";
        return new GeneratedFile(path, sb.toString());
    }

    private void appendJsonHelpers(StringBuilder sb) {
        sb.append("    static String toJson(Object value) {\n");
        sb.append("        if (value == null) return \"null\";\n");
        sb.append("        if (value instanceof String s)\n");
        sb.append("            return \"\\\"\" + s.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\").replace(\"\\n\", \"\\\\n\").replace(\"\\r\", \"\\\\r\").replace(\"\\t\", \"\\\\t\") + \"\\\"\";\n");
        sb.append("        if (value instanceof Number || value instanceof Boolean) return value.toString();\n");
        sb.append("        if (value instanceof java.util.Map<?, ?> map) {\n");
        sb.append("            var sb = new StringBuilder(\"{\");\n");
        sb.append("            boolean first = true;\n");
        sb.append("            for (var entry : map.entrySet()) {\n");
        sb.append("                if (!first) sb.append(\",\");\n");
        sb.append("                sb.append(\"\\\"\").append(entry.getKey()).append(\"\\\":\").append(toJson(entry.getValue()));\n");
        sb.append("                first = false;\n");
        sb.append("            }\n");
        sb.append("            return sb.append(\"}\").toString();\n");
        sb.append("        }\n");
        sb.append("        if (value instanceof Iterable<?> list) {\n");
        sb.append("            var sb = new StringBuilder(\"[\");\n");
        sb.append("            boolean first = true;\n");
        sb.append("            for (var item : list) {\n");
        sb.append("                if (!first) sb.append(\",\");\n");
        sb.append("                sb.append(toJson(item));\n");
        sb.append("                first = false;\n");
        sb.append("            }\n");
        sb.append("            return sb.append(\"]\").toString();\n");
        sb.append("        }\n");
        sb.append("        return toJson(value.toString());\n");
        sb.append("    }\n");
    }

    private void appendXmlHelpers(StringBuilder sb) {
        sb.append("    static String toXml(String root, java.util.Map<?, ?> fields) {\n");
        sb.append("        var sb = new StringBuilder(\"<\").append(root).append(\">\");\n");
        sb.append("        for (var e : fields.entrySet()) { appendXml(sb, String.valueOf(e.getKey()), e.getValue()); }\n");
        sb.append("        return sb.append(\"</\").append(root).append(\">\").toString();\n");
        sb.append("    }\n\n");
        sb.append("    private static void appendXml(StringBuilder sb, String tag, Object value) {\n");
        sb.append("        if (value instanceof Iterable<?> list) {\n");
        sb.append("            for (var item : list) { appendXml(sb, tag, item); }\n");
        sb.append("        } else if (value instanceof java.util.Map<?, ?> map) {\n");
        sb.append("            sb.append(\"<\").append(tag).append(\">\");\n");
        sb.append("            for (var e : map.entrySet()) { appendXml(sb, String.valueOf(e.getKey()), e.getValue()); }\n");
        sb.append("            sb.append(\"</\").append(tag).append(\">\");\n");
        sb.append("        } else if (value != null) {\n");
        sb.append("            sb.append(\"<\").append(tag).append(\">\").append(xmlEscape(String.valueOf(value))).append(\"</\").append(tag).append(\">\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private static String xmlEscape(String s) {\n");
        sb.append("        return s.replace(\"&\", \"&amp;\").replace(\"<\", \"&lt;\").replace(\">\", \"&gt;\").replace(\"\\\"\", \"&quot;\");\n");
        sb.append("    }\n");
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

    private String templateBody(Model model, OperationShape op, Protocol protocol) {
        if (protocol == Protocol.FORM_URL) {
            String name = op.getId().getName();
            return "<" + name + "Response><ResponseMetadata>"
                    + "<RequestId>{{randomValue type='UUID'}}</RequestId>"
                    + "</ResponseMetadata></" + name + "Response>";
        }

        StructureShape output = op.getOutput()
                .map(model::expectShape)
                .filter(s -> s instanceof StructureShape)
                .map(s -> (StructureShape) s)
                .filter(s -> !s.getAllMembers().isEmpty())
                .orElse(null);

        if (protocol == Protocol.REST_XML) {
            // restXml services (e.g. S3) expect XML bodies, not JSON. Empty output → empty body.
            return output == null ? "" : buildXmlTemplate(model, output);
        }
        // JSON_TARGET and REST_JSON
        return output == null ? "{}" : buildJsonTemplate(model, output);
    }

    private String buildJsonTemplate(Model model, StructureShape output) {
        String fields = output.getAllMembers().entrySet().stream()
                .limit(8)
                .map(e -> {
                    String name   = e.getKey();
                    Shape  target = model.expectShape(e.getValue().getTarget());
                    return "\"" + name + "\":" + jsonPlaceholder(target);
                })
                .collect(Collectors.joining(","));
        return "{" + fields + "}";
    }

    private String buildXmlTemplate(Model model, StructureShape output) {
        String root = output.getId().getName();
        String body = output.getAllMembers().entrySet().stream()
                .limit(8)
                .map(e -> {
                    String name   = e.getKey();
                    Shape  target = model.expectShape(e.getValue().getTarget());
                    return "<" + name + ">" + xmlPlaceholder(target) + "</" + name + ">";
                })
                .collect(Collectors.joining());
        return "<" + root + ">" + body + "</" + root + ">";
    }

    private String jsonPlaceholder(Shape shape) {
        return switch (shape.getType()) {
            case STRING  -> "\"{{randomValue type='UUID'}}\"";
            case INTEGER, LONG, SHORT, BYTE, BIG_INTEGER -> "0";
            case FLOAT, DOUBLE, BIG_DECIMAL             -> "0.0";
            case BOOLEAN                                 -> "false";
            case LIST, SET                               -> "[]";
            default                                      -> "{}";
        };
    }

    private String xmlPlaceholder(Shape shape) {
        return switch (shape.getType()) {
            case STRING  -> "{{randomValue type='UUID'}}";
            case INTEGER, LONG, SHORT, BYTE, BIG_INTEGER -> "0";
            case FLOAT, DOUBLE, BIG_DECIMAL             -> "0.0";
            case BOOLEAN                                 -> "false";
            default                                      -> "";
        };
    }

    private String[] httpMethodAndPath(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .map(h -> new String[]{
                        h.getMethod().toUpperCase(),
                        // Greedy labels {key+} span path segments (.+); normal labels {id} stay within one ([^/]+).
                        // Replace greedy labels first so the normal-label rule doesn't consume them.
                        h.getUri().toString().replaceAll("\\{[^}]*\\+}", ".+")
                                              .replaceAll("\\{[^}]+}", "[^/]+")
                })
                .orElse(new String[]{"POST", "/.*"});
    }
}

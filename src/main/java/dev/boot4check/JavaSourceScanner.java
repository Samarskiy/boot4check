package dev.boot4check;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks Java sources. Works on syntax only (no classpath, no symbol resolution), so it runs on any
 * checkout without building it. Types are matched by simple name, which is the main source of
 * false positives and the reason some rules are MEDIUM confidence.
 */
final class JavaSourceScanner {

    static final ParserConfiguration PARSER_CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private static final Set<String> JACKSON2_SERIALIZER_BASES = Set.of(
            "JsonSerializer", "JsonDeserializer", "StdSerializer", "StdDeserializer",
            "StdScalarSerializer", "StdScalarDeserializer", "KeyDeserializer");

    private static final Set<String> TEST_HTTP_CLIENTS = Set.of("TestRestTemplate", "WebTestClient");

    /** Parsed file plus where it lives, kept so rules can look across files. */
    record Source(Path file, CompilationUnit unit) {
    }

    private final List<Source> sources = new ArrayList<>();
    /** Simple type name -> primitive fields declared on it. Ambiguous names are dropped. */
    private final Map<String, List<String>> primitiveFieldsByType = new HashMap<>();
    private final Set<String> ambiguousTypes = new HashSet<>();

    void add(Path relativeFile, CompilationUnit unit) {
        sources.add(new Source(relativeFile, unit));
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            List<String> primitives = primitiveMembers(type);
            String name = type.getNameAsString();
            if (primitiveFieldsByType.containsKey(name) || ambiguousTypes.contains(name)) {
                primitiveFieldsByType.remove(name);
                ambiguousTypes.add(name);
            } else {
                primitiveFieldsByType.put(name, primitives);
            }
        }
    }

    List<Finding> scan() {
        List<Finding> findings = new ArrayList<>();
        for (Source source : sources) {
            scanTypes(source, findings);
            scanMethods(source, findings);
            scanAnnotations(source, findings);
            scanPropertyMapper(source, findings);
        }
        return findings;
    }

    private void scanTypes(Source source, List<Finding> findings) {
        CompilationUnit unit = source.unit();
        boolean importsJackson2Databind = imports(unit, "com.fasterxml.jackson.databind");

        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (importsJackson2Databind) {
                for (ClassOrInterfaceType parent : type.getExtendedTypes()) {
                    if (JACKSON2_SERIALIZER_BASES.contains(parent.getNameAsString())) {
                        findings.add(at(Rule.JACKSON2_SERIALIZER, source, type,
                                type.getNameAsString() + " extends " + parent.getNameAsString()));
                    }
                }
            }
            if (hasAnnotation(type, "SpringBootTest")) {
                scanSpringBootTest(source, type, findings);
            }
        }

        for (EnumDeclaration type : unit.findAll(EnumDeclaration.class)) {
            boolean overridesToString = type.getMethodsByName("toString").stream()
                    .anyMatch(method -> method.getParameters().isEmpty());
            if (overridesToString && !pinsJsonValue(type)) {
                findings.add(at(Rule.ENUM_TO_STRING, source, type, "enum " + type.getNameAsString()));
            }
        }

        for (ClassOrInterfaceType reference : unit.findAll(ClassOrInterfaceType.class)) {
            String name = reference.getNameAsString();
            if (name.equals("Jackson2ObjectMapperBuilderCustomizer") || name.equals("Jackson2ObjectMapperBuilder")) {
                findings.add(at(Rule.JACKSON2_CUSTOMIZER, source, reference, name));
            }
        }
    }

    private void scanSpringBootTest(Source source, ClassOrInterfaceDeclaration test, List<Finding> findings) {
        for (FieldDeclaration field : test.getFields()) {
            if (!isInjected(field)) {
                // A client the test builds itself (for example WebTestClient.bindToServer()) is unaffected.
                continue;
            }
            String type = simpleName(field.getElementType());
            if (type.equals("MockMvc") && !hasAnnotation(test, "AutoConfigureMockMvc")) {
                findings.add(at(Rule.MOCKMVC_NOT_AUTOCONFIGURED, source, field, test.getNameAsString()));
            }
            if (TEST_HTTP_CLIENTS.contains(type)
                    && !hasAnnotation(test, "AutoConfigure" + type)
                    && !hasAnnotation(test, "AutoConfigureRestTestClient")) {
                findings.add(at(Rule.TEST_CLIENT_NOT_AUTOCONFIGURED, source, field,
                        test.getNameAsString() + " injects " + type));
            }
        }
    }

    private void scanMethods(Source source, List<Finding> findings) {
        Set<String> reportedBodies = new HashSet<>();
        for (MethodDeclaration method : source.unit().findAll(MethodDeclaration.class)) {
            if (hasAnnotation(method, "Bean")) {
                String returns = simpleName(method.getType());
                if (returns.equals("ObjectMapper")) {
                    findings.add(at(Rule.OBJECT_MAPPER_BEAN, source, method,
                            "@Bean " + method.getNameAsString() + "()"));
                }
                if (returns.endsWith("HttpMessageConverter")) {
                    findings.add(at(Rule.MESSAGE_CONVERTER_BEAN, source, method,
                            "@Bean " + returns + " " + method.getNameAsString() + "()"));
                }
            }
            for (Parameter parameter : method.getParameters()) {
                if (!hasAnnotation(parameter, "RequestBody")) {
                    continue;
                }
                String bodyType = simpleName(parameter.getType());
                List<String> primitives = primitiveFieldsByType.getOrDefault(bodyType, List.of());
                if (!primitives.isEmpty() && reportedBodies.add(bodyType)) {
                    findings.add(at(Rule.REQUEST_BODY_PRIMITIVES, source, parameter,
                            bodyType + ": " + String.join(", ", primitives)));
                }
            }
        }
        for (MethodCallExpr call : source.unit().findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("alwaysApplyingWhenNonNull")) {
                findings.add(at(Rule.PROPERTY_MAPPER_REMOVED_METHOD, source, call, "alwaysApplyingWhenNonNull()"));
            }
        }
    }

    private void scanAnnotations(Source source, List<Finding> findings) {
        for (AnnotationExpr annotation : source.unit().findAll(AnnotationExpr.class)) {
            String name = annotation.getName().getIdentifier();
            if (name.equals("JsonComponent") || name.equals("JsonMixin")) {
                findings.add(at(Rule.JSON_COMPONENT, source, annotation, "@" + name));
            }
            if (name.equals("MockBean") || name.equals("SpyBean")) {
                findings.add(at(Rule.MOCK_BEAN_REMOVED, source, annotation, "@" + name));
            }
        }
    }

    private void scanPropertyMapper(Source source, List<Finding> findings) {
        source.unit().getImports().stream()
                .filter(imported -> imported.getNameAsString()
                        .equals("org.springframework.boot.context.properties.PropertyMapper"))
                .findFirst()
                .ifPresent(imported -> findings.add(at(Rule.PROPERTY_MAPPER, source, imported, "uses PropertyMapper")));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static List<String> primitiveMembers(TypeDeclaration<?> type) {
        List<String> names = new ArrayList<>();
        if (type instanceof RecordDeclaration record) {
            for (Parameter component : record.getParameters()) {
                if (component.getType().isPrimitiveType()) {
                    names.add(component.getType() + " " + component.getNameAsString());
                }
            }
        }
        for (FieldDeclaration field : type.getFields()) {
            if (field.isStatic() || !field.getElementType().isPrimitiveType()) {
                continue;
            }
            field.getVariables().forEach(variable ->
                    names.add(field.getElementType() + " " + variable.getNameAsString()));
        }
        return names;
    }

    /** True when the enum already fixes its wire format, so toString() is not what Jackson uses. */
    private static boolean pinsJsonValue(EnumDeclaration type) {
        return type.findAll(AnnotationExpr.class).stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .anyMatch(name -> name.equals("JsonValue") || name.equals("JsonProperty")
                        || name.equals("JsonCreator"));
    }

    private static boolean isInjected(FieldDeclaration field) {
        return hasAnnotation(field, "Autowired") || hasAnnotation(field, "Inject") || hasAnnotation(field, "Resource");
    }

    private static boolean imports(CompilationUnit unit, String packagePrefix) {
        return unit.getImports().stream().anyMatch(imported -> imported.getNameAsString().startsWith(packagePrefix));
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> annotation.getName().getIdentifier().equals(simpleName));
    }

    private static String simpleName(Type type) {
        return type.isClassOrInterfaceType() ? type.asClassOrInterfaceType().getNameAsString() : type.asString();
    }

    private static Finding at(Rule rule, Source source, Node node, String detail) {
        int line = node.getBegin().map(position -> position.line).orElse(0);
        return new Finding(rule, source.file(), line, detail);
    }
}

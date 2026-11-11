package dev.boot4check;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerTest {

    @TempDir
    Path project;

    // ---- Jackson -----------------------------------------------------------------------------

    @Test
    void flagsObjectMapperBeanButNotJsonMapperBean() throws IOException {
        java("JacksonConfig", """
                import com.fasterxml.jackson.databind.ObjectMapper;
                class JacksonConfig {
                    @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
                    @Bean JsonMapper jsonMapper() { return null; }
                    ObjectMapper notABean() { return new ObjectMapper(); }
                }
                """);

        List<Finding> findings = scan().findings();

        assertEquals(1, findings.size());
        assertEquals(Rule.OBJECT_MAPPER_BEAN, findings.get(0).rule());
        assertEquals(3, findings.get(0).line());
    }

    @Test
    void flagsJackson2Customizer() throws IOException {
        java("Customizer", """
                class Customizer implements Jackson2ObjectMapperBuilderCustomizer {
                    public void customize(Jackson2ObjectMapperBuilder builder) { }
                }
                """);

        assertEquals(List.of(Rule.JACKSON2_CUSTOMIZER, Rule.JACKSON2_CUSTOMIZER), rules());
    }

    @Test
    void flagsEnumWithToStringUnlessWireFormatIsPinned() throws IOException {
        java("Status", """
                enum Status {
                    ACTIVE;
                    @Override public String toString() { return "active"; }
                }
                """);
        java("Pinned", """
                enum Pinned {
                    ACTIVE;
                    @JsonValue public String code() { return "a"; }
                    @Override public String toString() { return "active"; }
                }
                """);
        java("Plain", "enum Plain { A, B }");

        List<Finding> findings = scan().findings();

        assertEquals(1, findings.size());
        assertEquals(Rule.ENUM_TO_STRING, findings.get(0).rule());
        assertEquals("enum Status", findings.get(0).detail());
    }

    @Test
    void flagsRequestBodyWithPrimitiveFieldsAcrossFiles() throws IOException {
        java("OrderRequest", """
                class OrderRequest {
                    static final int MAX = 10;
                    private int quantity;
                    private boolean express;
                    private Integer optional;
                }
                """);
        java("Money", "record Money(long cents, String currency) { }");
        java("Safe", "class Safe { private Integer quantity; }");
        java("OrderController", """
                class OrderController {
                    void create(@RequestBody OrderRequest request) { }
                    void pay(@RequestBody Money money) { }
                    void safe(@RequestBody Safe safe) { }
                    void notABody(OrderRequest request) { }
                }
                """);

        List<Finding> findings = scan().findings();

        assertEquals(2, findings.size());
        assertEquals("OrderRequest: int quantity, boolean express", findings.get(0).detail());
        assertEquals("Money: long cents", findings.get(1).detail());
    }

    @Test
    void flagsJackson2SerializerOnlyWhenJackson2IsImported() throws IOException {
        java("MoneySerializer", """
                import com.fasterxml.jackson.databind.JsonSerializer;
                class MoneySerializer extends JsonSerializer<String> { }
                """);
        java("Unrelated", "class Unrelated extends JsonSerializer<String> { }");

        List<Finding> findings = scan().findings();

        assertEquals(1, findings.size());
        assertEquals(Rule.JACKSON2_SERIALIZER, findings.get(0).rule());
        assertEquals(Path.of("src/main/java/MoneySerializer.java"), findings.get(0).file());
    }

    @Test
    void jackson2DefaultsPropertyMutesDefaultChangeRulesOnly() throws IOException {
        java("Status", "enum Status { A; public String toString() { return \"a\"; } }");
        java("Config", "class Config { @Bean ObjectMapper mapper() { return null; } }");
        resource("application.properties", "spring.jackson.use-jackson2-defaults=true\n");

        ScanResult result = scan();

        assertTrue(result.jackson2Defaults());
        assertEquals(List.of(Rule.OBJECT_MAPPER_BEAN), result.findings().stream().map(Finding::rule).toList());
    }

    // ---- Spring Boot core and tests ------------------------------------------------------------

    @Test
    void flagsPropertyMapperUsageAndRemovedMethod() throws IOException {
        java("Mapping", """
                import org.springframework.boot.context.properties.PropertyMapper;
                class Mapping {
                    void apply() { PropertyMapper.get().alwaysApplyingWhenNonNull(); }
                }
                """);

        assertEquals(List.of(Rule.PROPERTY_MAPPER, Rule.PROPERTY_MAPPER_REMOVED_METHOD), rules());
    }

    @Test
    void flagsSpringBootTestMissingAutoConfiguration() throws IOException {
        java("BrokenTest", """
                @SpringBootTest
                class BrokenTest {
                    @Autowired MockMvc mvc;
                    @Autowired TestRestTemplate rest;
                }
                """);
        java("FineTest", """
                @SpringBootTest
                @AutoConfigureMockMvc
                @AutoConfigureTestRestTemplate
                class FineTest {
                    @Autowired MockMvc mvc;
                    @Autowired TestRestTemplate rest;
                }
                """);
        java("SliceTest", "@WebMvcTest class SliceTest { @Autowired MockMvc mvc; }");

        assertEquals(List.of(Rule.MOCKMVC_NOT_AUTOCONFIGURED, Rule.TEST_CLIENT_NOT_AUTOCONFIGURED), rules());
    }

    @Test
    void flagsRemovedAnnotations() throws IOException {
        java("Legacy", """
                @JsonComponent
                class Legacy {
                    @MockBean Object mock;
                    @SpyBean Object spy;
                    @MockitoBean Object fine;
                }
                """);

        assertEquals(List.of(Rule.JSON_COMPONENT, Rule.MOCK_BEAN_REMOVED, Rule.MOCK_BEAN_REMOVED), rules());
    }

    // ---- Build files ---------------------------------------------------------------------------

    @Test
    void flagsMavenDependencies() throws IOException {
        write("pom.xml", """
                <project>
                  <dependencies>
                    <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>
                    <dependency><artifactId>spring-boot-starter-undertow</artifactId></dependency>
                    <dependency><artifactId>flyway-core</artifactId></dependency>
                    <dependency><artifactId>spring-boot-starter-batch</artifactId></dependency>
                  </dependencies>
                </project>
                """);

        assertEquals(List.of(Rule.FLYWAY_LIQUIBASE_STARTER, Rule.BATCH_IN_MEMORY,
                Rule.UNDERTOW_REMOVED, Rule.STARTER_RENAMED), rules());
    }

    @Test
    void flagsGradleDependenciesInBothNotations() throws IOException {
        write("build.gradle.kts", """
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("org.liquibase:liquibase-core:4.29.0")
                }
                """);

        List<Finding> findings = scan().findings();

        assertEquals(List.of(Rule.FLYWAY_LIQUIBASE_STARTER, Rule.STARTER_RENAMED),
                findings.stream().map(Finding::rule).toList());
        assertEquals(3, findings.get(0).line());
    }

    @Test
    void newStartersSatisfyTheBuildRules() throws IOException {
        write("pom.xml", """
                <artifactId>spring-boot-starter-webmvc</artifactId>
                <artifactId>flyway-core</artifactId>
                <artifactId>spring-boot-starter-flyway</artifactId>
                <artifactId>spring-boot-starter-batch</artifactId>
                <artifactId>spring-boot-starter-batch-jdbc</artifactId>
                """);

        assertEquals(List.of(), rules());
    }

    @Test
    void starterDeclaredInAnotherModuleCounts() throws IOException {
        write("pom.xml", "<artifactId>spring-boot-starter-flyway</artifactId>\n");
        write("service/pom.xml", "<artifactId>flyway-core</artifactId>\n");

        assertEquals(List.of(), rules());
    }

    // ---- Configuration ---------------------------------------------------------------------------

    @Test
    void flagsRenamedJacksonPropertiesInPropertiesFile() throws IOException {
        resource("application.properties", """
                # spring.jackson.write.indent-output=true
                spring.jackson.write.indent-output=true
                spring.jackson.parser.allow-comments = true
                spring.jackson.json.read.allow-comments=true
                spring.jackson.serialization.indent-output=true
                """);

        List<Finding> findings = scan().findings();

        assertEquals(2, findings.size());
        assertEquals(2, findings.get(0).line());
        assertEquals("spring.jackson.write.indent-output", findings.get(0).detail());
        assertEquals("spring.jackson.parser.allow-comments", findings.get(1).detail());
    }

    @Test
    void flagsRenamedJacksonPropertiesInNestedYaml() throws IOException {
        resource("application-prod.yml", """
                spring:
                  jackson:
                    read:
                      allow-comments: true
                    json:
                      write:
                        indent-output: true
                ---
                spring.jackson.write.indent-output: true
                """);

        List<Finding> findings = scan().findings();

        assertEquals(2, findings.size());
        assertEquals("spring.jackson.read.allow-comments", findings.get(0).detail());
        assertEquals(4, findings.get(0).line());
        assertEquals(9, findings.get(1).line());
    }

    // ---- Robustness -------------------------------------------------------------------------------

    @Test
    void reportsUnparsableFilesInsteadOfHidingThem() throws IOException {
        java("Broken", "class Broken { this is not java");
        resource("application.yml", "key: [unclosed");
        java("Status", "enum Status { A; public String toString() { return \"a\"; } }");

        ScanResult result = scan();

        assertEquals(List.of("src/main/java/Broken.java", "src/main/resources/application.yml"),
                result.unparsedFiles());
        assertEquals(1, result.findings().size());
    }

    @Test
    void skipsBuildOutputDirectories() throws IOException {
        write("target/generated/Status.java", "enum Status { A; public String toString() { return \"a\"; } }");

        assertEquals(0, scan().javaFiles());
    }

    @Test
    void cleanProjectHasNoFindings() throws IOException {
        java("Clean", "record Clean(String name, Integer count) { }");

        ScanResult result = scan();

        assertTrue(result.findings().isEmpty());
        assertFalse(result.jackson2Defaults());
        assertEquals(1, result.javaFiles());
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private ScanResult scan() throws IOException {
        return new Scanner().scan(project);
    }

    private List<Rule> rules() throws IOException {
        return scan().findings().stream().map(Finding::rule).toList();
    }

    private void java(String className, String source) throws IOException {
        write("src/main/java/" + className + ".java", source);
    }

    private void resource(String name, String content) throws IOException {
        write("src/main/resources/" + name, content);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = project.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

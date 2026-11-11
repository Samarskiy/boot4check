package dev.boot4check;

/**
 * Every check the scanner knows. Each rule cites the document it is based on so a reviewer can
 * verify it; nothing here is taken from blog posts alone.
 */
public enum Rule {

    // ---- Jackson 3 -------------------------------------------------------------------------

    OBJECT_MAPPER_BEAN("B4C-J001", Category.BEHAVIOR, Confidence.HIGH,
            "ObjectMapper bean no longer replaces the auto-configured mapper",
            "Spring Boot 4 auto-configures a Jackson 3 JsonMapper. An ObjectMapper bean is no longer "
                    + "enough to replace it, so the settings in this bean stop applying to HTTP JSON.",
            "Define a tools.jackson.databind.json.JsonMapper bean, or move the settings into a "
                    + "JsonMapperBuilderCustomizer.",
            Docs.BOOT_GUIDE),

    JACKSON2_CUSTOMIZER("B4C-J002", Category.BEHAVIOR, Confidence.HIGH,
            "Jackson2ObjectMapperBuilder(Customizer) does not configure the Jackson 3 mapper",
            "Jackson2ObjectMapperBuilderCustomizer was replaced by JsonMapperBuilderCustomizer and "
                    + "Jackson2ObjectMapperBuilder has no Spring replacement. Kept alive through the "
                    + "deprecated spring-boot-jackson2 module, this code no longer affects the default mapper.",
            "Rewrite as a JsonMapperBuilderCustomizer (for example builder.enable(SerializationFeature.INDENT_OUTPUT)).",
            Docs.BOOT_GUIDE),

    ENUM_TO_STRING("B4C-J003", Category.BEHAVIOR, Confidence.MEDIUM,
            "Enum overrides toString(): its JSON value changes",
            "Jackson 3 enables WRITE_ENUMS_USING_TO_STRING and READ_ENUMS_USING_TO_STRING by default. "
                    + "Jackson 2 used name(). If this enum is serialized, the wire value changes from the "
                    + "constant name to whatever toString() returns, in both directions.",
            "Pin the wire format with @JsonValue / @JsonProperty, or set "
                    + "spring.jackson.use-jackson2-defaults=true while you migrate.",
            Docs.JACKSON_DEFAULTS),

    REQUEST_BODY_PRIMITIVES("B4C-J004", Category.BEHAVIOR, Confidence.MEDIUM,
            "Request body with primitive fields now rejects JSON null",
            "Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default. Clients that send an explicit "
                    + "null for these fields used to get the primitive default (0 / false); they now get an error.",
            "Use wrapper types (Integer, Boolean, ...) where null is legitimate, or set "
                    + "spring.jackson.use-jackson2-defaults=true while you migrate.",
            Docs.JACKSON_DEFAULTS),

    JACKSON2_SERIALIZER("B4C-J005", Category.BEHAVIOR, Confidence.MEDIUM,
            "Jackson 2 serializer/deserializer is not used by the Jackson 3 mapper",
            "This class extends a com.fasterxml.jackson.databind type. Jackson 3 lives in tools.jackson "
                    + "and does not see Jackson 2 serializers, so the type falls back to default serialization.",
            "Port it to tools.jackson.databind.ValueSerializer / ValueDeserializer (or the Std* base classes there).",
            Docs.JACKSON_SPRING),

    JSON_COMPONENT("B4C-J006", Category.BUILD, Confidence.HIGH,
            "@JsonComponent / @JsonMixin were renamed",
            "Spring Boot 4 renames @JsonComponent to @JacksonComponent and @JsonMixin to @JacksonMixin.",
            "Rename the annotation and port the nested serializers to Jackson 3.",
            Docs.BOOT_GUIDE),

    // ---- Spring Boot core --------------------------------------------------------------------

    PROPERTY_MAPPER("B4C-P001", Category.BEHAVIOR, Confidence.MEDIUM,
            "PropertyMapper no longer maps null values by default",
            "PropertyMapper no longer calls adapter or predicate methods when the source value is null. "
                    + "Mappings that relied on null flowing through are now skipped without an error.",
            "Review each mapping in this class; add .always() where null must still be applied.",
            Docs.BOOT_GUIDE),

    PROPERTY_MAPPER_REMOVED_METHOD("B4C-P002", Category.BUILD, Confidence.HIGH,
            "PropertyMapper.alwaysApplyingWhenNonNull() was removed",
            "The method no longer exists; skipping null is now the default behaviour.",
            "Delete the call.",
            Docs.BOOT_GUIDE),

    MESSAGE_CONVERTER_BEAN("B4C-W001", Category.BEHAVIOR, Confidence.MEDIUM,
            "HttpMessageConverter bean may no longer be picked up",
            "The migration guide says contributing HttpMessageConverter beans is replaced by "
                    + "ClientHttpMessageConvertersCustomizer and ServerHttpMessageConvertersCustomizer.",
            "Register the converter through the new customizer interfaces and verify it with a request-level test.",
            Docs.BOOT_GUIDE),

    // ---- Tests -----------------------------------------------------------------------------

    MOCKMVC_NOT_AUTOCONFIGURED("B4C-T001", Category.STARTUP, Confidence.MEDIUM,
            "@SpringBootTest no longer provides MockMvc",
            "This test injects MockMvc but is not annotated with @AutoConfigureMockMvc.",
            "Add @AutoConfigureMockMvc to the test class.",
            Docs.BOOT_GUIDE),

    TEST_CLIENT_NOT_AUTOCONFIGURED("B4C-T002", Category.STARTUP, Confidence.MEDIUM,
            "@SpringBootTest no longer provides TestRestTemplate / WebTestClient",
            "This test injects a test HTTP client but does not auto-configure it.",
            "Add @AutoConfigureTestRestTemplate (plus spring-boot-resttestclient) or @AutoConfigureWebTestClient.",
            Docs.BOOT_GUIDE),

    MOCK_BEAN_REMOVED("B4C-T003", Category.BUILD, Confidence.HIGH,
            "@MockBean / @SpyBean were removed",
            "Both annotations are gone in Spring Boot 4.",
            "Use @MockitoBean / @MockitoSpyBean. They cannot be declared in @Configuration classes.",
            Docs.BOOT_GUIDE),

    // ---- Build files -----------------------------------------------------------------------

    UNDERTOW_REMOVED("B4C-B001", Category.BUILD, Confidence.HIGH,
            "Undertow support was removed",
            "spring-boot-starter-undertow no longer exists because Undertow is not compatible with Servlet 6.1.",
            "Switch to Tomcat or Jetty and re-test anything tuned for Undertow.",
            Docs.BOOT_GUIDE),

    FLYWAY_LIQUIBASE_STARTER("B4C-B002", Category.BEHAVIOR, Confidence.HIGH,
            "Database migrations need the new Flyway / Liquibase starter",
            "With Spring Boot 4 the bare flyway/liquibase dependency no longer brings the auto-configuration "
                    + "with it. Without the starter the application can start with no migrations applied.",
            "Add spring-boot-starter-flyway or spring-boot-starter-liquibase.",
            Docs.BOOT_GUIDE),

    BATCH_IN_MEMORY("B4C-B003", Category.BEHAVIOR, Confidence.HIGH,
            "Spring Batch metadata is now kept in memory",
            "spring-boot-starter-batch now runs Spring Batch in memory. Job metadata is no longer written "
                    + "to your database, so restartability and job history disappear.",
            "Use spring-boot-starter-batch-jdbc to keep storing metadata in the database.",
            Docs.BOOT_GUIDE),

    STARTER_RENAMED("B4C-B004", Category.BUILD, Confidence.HIGH,
            "Starter was renamed or deprecated",
            "This starter has a new name in Spring Boot 4.",
            "Replace it with the new starter.",
            Docs.BOOT_GUIDE),

    EXECUTABLE_JAR("B4C-B005", Category.BUILD, Confidence.MEDIUM,
            "Fully executable jars (embedded launch script) were removed",
            "The build asks for an executable jar with an embedded launch script, which Spring Boot 4 no longer supports.",
            "Run with java -jar, or use another launcher such as Gradle's application plugin.",
            Docs.BOOT_GUIDE),

    // ---- Configuration ---------------------------------------------------------------------

    JACKSON_PROPERTY_RENAMED("B4C-C001", Category.BEHAVIOR, Confidence.HIGH,
            "Jackson property was renamed and is now ignored",
            "spring.jackson.read.*, spring.jackson.write.* and spring.jackson.parser.* moved under "
                    + "spring.jackson.json.*. The old key is not an error; it simply stops having any effect.",
            "Rename the key (read/parser -> spring.jackson.json.read, write -> spring.jackson.json.write).",
            Docs.BOOT_GUIDE);

    private final String id;
    private final Category category;
    private final Confidence confidence;
    private final String title;
    private final String why;
    private final String fix;
    private final String docUrl;

    Rule(String id, Category category, Confidence confidence, String title, String why, String fix, String docUrl) {
        this.id = id;
        this.category = category;
        this.confidence = confidence;
        this.title = title;
        this.why = why;
        this.fix = fix;
        this.docUrl = docUrl;
    }

    public String id() { return id; }
    public Category category() { return category; }
    public Confidence confidence() { return confidence; }
    public String title() { return title; }
    public String why() { return why; }
    public String fix() { return fix; }
    public String docUrl() { return docUrl; }

    /** Rules that only matter while Jackson 3 defaults are in effect. */
    public boolean dependsOnJackson3Defaults() {
        return this == ENUM_TO_STRING || this == REQUEST_BODY_PRIMITIVES;
    }

    static final class Docs {
        static final String BOOT_GUIDE =
                "https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide";
        static final String JACKSON_DEFAULTS =
                "https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-2";
        static final String JACKSON_SPRING =
                "https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/";

        private Docs() {
        }
    }
}

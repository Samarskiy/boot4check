# Boot4Check

Finds the Spring Boot 3 → 4 upgrade problems that compile cleanly and break at runtime.

Rewrite tools handle the mechanical part of the upgrade (renamed classes, moved packages). Boot4Check
looks for what is left afterwards: code that still builds and starts but behaves differently, such as an
`ObjectMapper` bean that no longer configures HTTP JSON, or a Flyway dependency that no longer runs migrations.

**Status: prototype.** The rule set is small and has not been validated on a real upgrade yet.

## Usage

Requires Java 17 or newer.

```
mvn package
java -jar target/boot4check-0.1.0-SNAPSHOT.jar /path/to/your/project
```

Options:

| Option | Effect |
| --- | --- |
| `--json` | Print the report as JSON |
| `--fail-on-findings` | Exit with status 1 when anything is found (for CI) |

The scanner only reads files. It does not build or run your project and sends nothing anywhere.

## What it checks

Findings are grouped by how you would otherwise discover them:

- **Silent behaviour change**: compiles, starts, behaves differently.
- **Fails at startup**: compiles, but the application or its tests fail on start.
- **Fails the build**: you would find out immediately; listed for completeness.

| Rule | Category | What it finds |
| --- | --- | --- |
| B4C-J001 | Silent | `@Bean ObjectMapper` no longer replaces the auto-configured Jackson 3 `JsonMapper` |
| B4C-J002 | Silent | `Jackson2ObjectMapperBuilderCustomizer` / `Jackson2ObjectMapperBuilder` do not configure the Jackson 3 mapper |
| B4C-J003 | Silent | Enum overrides `toString()`: Jackson 3 uses it as the JSON value by default |
| B4C-J004 | Silent | `@RequestBody` type with primitive fields: JSON `null` is now rejected |
| B4C-J005 | Silent | Jackson 2 serializer/deserializer subclass is invisible to the Jackson 3 mapper |
| B4C-P001 | Silent | `PropertyMapper` no longer maps `null` values by default |
| B4C-W001 | Silent | `HttpMessageConverter` bean replaced by the converter customizer interfaces |
| B4C-B002 | Silent | Flyway / Liquibase without the new `spring-boot-starter-flyway` / `-liquibase` |
| B4C-B003 | Silent | `spring-boot-starter-batch` now keeps job metadata in memory |
| B4C-C001 | Silent | `spring.jackson.read.*`, `write.*`, `parser.*` were renamed and are now ignored |
| B4C-T001 | Startup | `@SpringBootTest` injects `MockMvc` without `@AutoConfigureMockMvc` |
| B4C-T002 | Startup | `@SpringBootTest` injects `TestRestTemplate` / `WebTestClient` without auto-configuring it |
| B4C-J006 | Build | `@JsonComponent` / `@JsonMixin` renamed |
| B4C-P002 | Build | `PropertyMapper.alwaysApplyingWhenNonNull()` removed |
| B4C-T003 | Build | `@MockBean` / `@SpyBean` removed |
| B4C-B001 | Build | Undertow support removed |
| B4C-B004 | Build | Renamed or deprecated starters |
| B4C-B005 | Build | Fully executable jars removed |

Every finding in the report links to the document the rule is based on: the
[Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide),
the [Jackson 3 default changes (JSTEP-2)](https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-2) or the
[Spring blog post on Jackson 3 support](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/).

If the project sets `spring.jackson.use-jackson2-defaults=true`, the rules that depend on Jackson 3 defaults
(J003, J004) are skipped and the report says so.

## Limitations

- **Syntax only.** The scanner does not resolve types or the classpath. Types are matched by simple name, so a
  class of your own named `ObjectMapper` would be flagged. Rules that rely on this are marked `MEDIUM` confidence.
- **Declared dependencies only.** Build files are matched as text. Dependencies that arrive transitively, through a
  parent POM outside the repository, or through a version catalog alias are not seen.
- **Inheritance and constructor injection are not followed.** A test whose base class carries `@AutoConfigureMockMvc`
  is still flagged (T001); a test that receives `MockMvc` through its constructor is missed. Only fields annotated
  `@Autowired`, `@Inject` or `@Resource` are checked.
- **J003 and J004 do not know whether a type is really serialized.** J003 flags every enum that overrides
  `toString()`; J004 only looks at fields declared directly on the `@RequestBody` type, not on nested or parent types.
- **A clean report is not proof of a safe upgrade.** The scanner checks the fixed list above and nothing else.
- Files that cannot be parsed are listed in the report and are not checked.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).

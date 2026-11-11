package dev.boot4check;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks pom.xml and Gradle build scripts. Matches artifact names in the text instead of resolving
 * the dependency graph, so it sees only what the project declares itself, not what arrives transitively.
 */
final class BuildFileScanner {

    private static final Map<String, String> RENAMED_STARTERS = Map.of(
            "spring-boot-starter-web", "spring-boot-starter-webmvc",
            "spring-boot-starter-aop", "spring-boot-starter-aspectj",
            "spring-boot-starter-web-services", "spring-boot-starter-webservices",
            "spring-boot-starter-oauth2-client", "spring-boot-starter-security-oauth2-client",
            "spring-boot-starter-oauth2-resource-server", "spring-boot-starter-security-oauth2-resource-server",
            "spring-boot-starter-oauth2-authorization-server",
            "spring-boot-starter-security-oauth2-authorization-server");

    /** An artifact id is delimited by XML tags in Maven and by quotes or colons in Gradle. */
    private static final Pattern ARTIFACT = Pattern.compile("(?<=[>:'\"])([a-z0-9][a-z0-9.-]*)(?=[<:'\"])");

    private static final Pattern EXECUTABLE_JAR = Pattern.compile(
            "<executable>\\s*true\\s*</executable>|launchScript\\s*[({]");

    private record Declared(Path file, int line) {
    }

    /** First declaration of every artifact across all build files of the project. */
    private final Map<String, Declared> declared = new LinkedHashMap<>();
    private final List<Finding> findings = new ArrayList<>();

    void add(Path relativeFile, List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            Matcher matcher = ARTIFACT.matcher(line);
            while (matcher.find()) {
                declared.putIfAbsent(matcher.group(1), new Declared(relativeFile, lineNumber));
            }
            if (EXECUTABLE_JAR.matcher(line).find()) {
                findings.add(new Finding(Rule.EXECUTABLE_JAR, relativeFile, lineNumber, line.strip()));
            }
        }
    }

    List<Finding> scan() {
        List<Finding> result = new ArrayList<>(findings);

        report(result, Rule.UNDERTOW_REMOVED, "spring-boot-starter-undertow", "spring-boot-starter-undertow");

        if (!declared.containsKey("spring-boot-starter-flyway")) {
            declared.keySet().stream()
                    .filter(artifact -> artifact.equals("flyway-core") || artifact.startsWith("flyway-database-")
                            || artifact.equals("flyway-mysql") || artifact.equals("flyway-sqlserver"))
                    .findFirst()
                    .ifPresent(artifact -> report(result, Rule.FLYWAY_LIQUIBASE_STARTER, artifact,
                            artifact + " without spring-boot-starter-flyway"));
        }
        if (!declared.containsKey("spring-boot-starter-liquibase")) {
            report(result, Rule.FLYWAY_LIQUIBASE_STARTER, "liquibase-core",
                    "liquibase-core without spring-boot-starter-liquibase");
        }
        if (!declared.containsKey("spring-boot-starter-batch-jdbc")) {
            report(result, Rule.BATCH_IN_MEMORY, "spring-boot-starter-batch", "spring-boot-starter-batch");
        }
        RENAMED_STARTERS.forEach((oldName, newName) ->
                report(result, Rule.STARTER_RENAMED, oldName, oldName + " -> " + newName));
        return result;
    }

    private void report(List<Finding> result, Rule rule, String artifact, String detail) {
        Declared where = declared.get(artifact);
        if (where != null) {
            result.add(new Finding(rule, where.file(), where.line(), detail));
        }
    }
}

package dev.boot4check;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Walks a project directory and runs every rule. Reads files only; never executes the project's build. */
public final class Scanner {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", ".mvn", "target", "build", "out", "node_modules");

    private static final Set<String> BUILD_FILES = Set.of("pom.xml", "build.gradle", "build.gradle.kts");

    public ScanResult scan(Path root) throws IOException {
        JavaSourceScanner java = new JavaSourceScanner();
        BuildFileScanner build = new BuildFileScanner();
        ConfigScanner config = new ConfigScanner();
        JavaParser parser = new JavaParser(JavaSourceScanner.PARSER_CONFIG);
        List<String> unparsed = new ArrayList<>();
        int[] counts = new int[3];

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                boolean skip = !directory.equals(root) && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString());
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String name = file.getFileName().toString();
                Path relative = root.relativize(file);
                try {
                    if (name.endsWith(".java")) {
                        ParseResult<CompilationUnit> parsed = parser.parse(file);
                        if (parsed.getResult().isPresent() && parsed.isSuccessful()) {
                            java.add(relative, parsed.getResult().get());
                            counts[0]++;
                        } else {
                            unparsed.add(relative.toString());
                        }
                    } else if (BUILD_FILES.contains(name)) {
                        build.add(relative, Files.readAllLines(file, StandardCharsets.UTF_8));
                        counts[1]++;
                    } else if (name.startsWith("application") && name.endsWith(".properties")) {
                        config.addProperties(relative, Files.readAllLines(file, StandardCharsets.UTF_8));
                        counts[2]++;
                    } else if (name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml"))) {
                        config.addYaml(relative, Files.readString(file, StandardCharsets.UTF_8));
                        counts[2]++;
                    }
                } catch (IOException | RuntimeException unreadable) {
                    // Malformed YAML, bad encoding and the like. Report it rather than hide a file we did not check.
                    unparsed.add(relative.toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<Finding> findings = new ArrayList<>();
        findings.addAll(java.scan());
        findings.addAll(build.scan());
        findings.addAll(config.findings());
        if (config.usesJackson2Defaults()) {
            findings.removeIf(finding -> finding.rule().dependsOnJackson3Defaults());
        }
        Collections.sort(findings);
        Collections.sort(unparsed);
        return new ScanResult(List.copyOf(findings), counts[0], counts[1], counts[2],
                List.copyOf(unparsed), config.usesJackson2Defaults());
    }
}

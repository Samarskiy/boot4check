package dev.boot4check;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path project;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void textReportListsFindingWithLocationAndFix() throws IOException {
        Files.writeString(project.resolve("Status.java"),
                "enum Status { A; public String toString() { return \"a\"; } }");

        int exit = run(project.toString());

        assertEquals(0, exit);
        String report = out.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("[B4C-J003]"), report);
        assertTrue(report.contains("Status.java:1  enum Status"), report);
        assertTrue(report.contains("Summary: 1 silent, 0 startup, 0 build"), report);
    }

    @Test
    void jsonReportEscapesQuotesAndBackslashes() throws IOException {
        Files.writeString(project.resolve("pom.xml"), "<executable>true</executable> \"C:\\tmp\"\n");

        run("--json", project.toString());

        String report = out.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("\"rule\": \"B4C-B005\""), report);
        assertTrue(report.contains("\\\"C:\\\\tmp\\\""), report);
    }

    @Test
    void failOnFindingsControlsExitCode() throws IOException {
        assertEquals(0, run("--fail-on-findings", project.toString()));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>spring-boot-starter-undertow</artifactId>");
        assertEquals(1, run("--fail-on-findings", project.toString()));
    }

    @Test
    void rejectsUnknownOptionAndMissingDirectory() {
        assertEquals(2, run("--nope"));
        assertEquals(2, run(project.resolve("missing").toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Not a directory"));
    }

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }
}

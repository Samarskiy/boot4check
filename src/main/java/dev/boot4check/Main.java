package dev.boot4check;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {

    private static final String USAGE = """
            Usage: boot4check [options] [project-directory]

            Scans a Spring Boot 3 project for problems it will hit on Spring Boot 4.
            Reads source files only. Nothing is built, executed or uploaded.

            Options:
              --json               print the report as JSON
              --fail-on-findings   exit with status 1 when anything is found (for CI)
              -h, --help           show this help
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        boolean json = false;
        boolean failOnFindings = false;
        Path root = Path.of(".");
        for (String arg : args) {
            switch (arg) {
                case "--json" -> json = true;
                case "--fail-on-findings" -> failOnFindings = true;
                case "-h", "--help" -> {
                    out.print(USAGE);
                    return 0;
                }
                default -> {
                    if (arg.startsWith("-")) {
                        err.println("Unknown option: " + arg);
                        err.print(USAGE);
                        return 2;
                    }
                    root = Path.of(arg);
                }
            }
        }
        if (!Files.isDirectory(root)) {
            err.println("Not a directory: " + root);
            return 2;
        }
        try {
            ScanResult result = new Scanner().scan(root.toAbsolutePath().normalize());
            out.print(json ? Report.json(result) : Report.text(result));
            return failOnFindings && !result.findings().isEmpty() ? 1 : 0;
        } catch (IOException failure) {
            err.println("Scan failed: " + failure.getMessage());
            return 2;
        }
    }
}

package dev.boot4check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a scan result as text for people or JSON for tools. */
final class Report {

    private Report() {
    }

    static String text(ScanResult result) {
        StringBuilder out = new StringBuilder();
        out.append("Boot4Check: Spring Boot 4 upgrade scan\n");
        out.append(String.format("Scanned %d Java files, %d build files, %d config files%n%n",
                result.javaFiles(), result.buildFiles(), result.configFiles()));

        if (result.findings().isEmpty()) {
            out.append("No known upgrade problems found. This scanner checks a fixed list of rules; ")
                    .append("it does not prove the upgrade is safe.\n");
        }

        for (Category category : Category.values()) {
            Map<Rule, List<Finding>> byRule = new LinkedHashMap<>();
            for (Finding finding : result.findings()) {
                if (finding.rule().category() == category) {
                    byRule.computeIfAbsent(finding.rule(), rule -> new ArrayList<>()).add(finding);
                }
            }
            if (byRule.isEmpty()) {
                continue;
            }
            out.append(String.format("== %s (%d) ==%n%n", category.label(), result.count(category)));
            byRule.forEach((rule, findings) -> {
                out.append(String.format("[%s] %s  (confidence: %s)%n", rule.id(), rule.title(), rule.confidence()));
                out.append("  Why: ").append(rule.why()).append('\n');
                out.append("  Fix: ").append(rule.fix()).append('\n');
                out.append("  Ref: ").append(rule.docUrl()).append('\n');
                for (Finding finding : findings) {
                    out.append(String.format("    %s:%d  %s%n", finding.file(), finding.line(), finding.detail()));
                }
                out.append('\n');
            });
        }

        if (result.jackson2Defaults()) {
            out.append("Note: ").append(ConfigScanner.JACKSON2_DEFAULTS_KEY)
                    .append("=true is set, so Jackson default-change rules were skipped. ")
                    .append("They will apply again when you remove that property.\n");
        }
        if (!result.unparsedFiles().isEmpty()) {
            out.append(String.format("Warning: %d file(s) could not be parsed and were NOT checked:%n",
                    result.unparsedFiles().size()));
            result.unparsedFiles().forEach(file -> out.append("    ").append(file).append('\n'));
        }
        out.append(String.format("%nSummary: %d silent, %d startup, %d build%n",
                result.count(Category.BEHAVIOR), result.count(Category.STARTUP), result.count(Category.BUILD)));
        return out.toString();
    }

    static String json(ScanResult result) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"summary\": {");
        out.append("\"behavior\": ").append(result.count(Category.BEHAVIOR));
        out.append(", \"startup\": ").append(result.count(Category.STARTUP));
        out.append(", \"build\": ").append(result.count(Category.BUILD));
        out.append(", \"javaFiles\": ").append(result.javaFiles());
        out.append(", \"jackson2Defaults\": ").append(result.jackson2Defaults());
        out.append("},\n  \"unparsedFiles\": [");
        appendJoined(out, result.unparsedFiles().stream().map(Report::quote).toList());
        out.append("],\n  \"findings\": [");
        List<String> items = new ArrayList<>();
        for (Finding finding : result.findings()) {
            Rule rule = finding.rule();
            items.add("\n    {\"rule\": " + quote(rule.id())
                    + ", \"category\": " + quote(rule.category().name())
                    + ", \"confidence\": " + quote(rule.confidence().name())
                    + ", \"title\": " + quote(rule.title())
                    + ", \"file\": " + quote(finding.file().toString().replace('\\', '/'))
                    + ", \"line\": " + finding.line()
                    + ", \"detail\": " + quote(finding.detail())
                    + ", \"fix\": " + quote(rule.fix())
                    + ", \"ref\": " + quote(rule.docUrl()) + "}");
        }
        appendJoined(out, items);
        out.append(items.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return out.toString();
    }

    private static void appendJoined(StringBuilder out, List<String> items) {
        out.append(String.join(", ", items));
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

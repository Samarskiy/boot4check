package dev.boot4check;

import java.util.List;

/**
 * @param findings         sorted, with rules muted by project configuration already removed
 * @param unparsedFiles    files the scanner could not read; they are reported, never silently skipped
 * @param jackson2Defaults the project sets spring.jackson.use-jackson2-defaults=true
 */
public record ScanResult(List<Finding> findings, int javaFiles, int buildFiles, int configFiles,
                         List<String> unparsedFiles, boolean jackson2Defaults) {

    public long count(Category category) {
        return findings.stream().filter(finding -> finding.rule().category() == category).count();
    }
}

package dev.boot4check;

import java.nio.file.Path;

/**
 * One problem at one place.
 *
 * @param file   path relative to the scanned root
 * @param line   1-based line, or 0 when the finding is about the file as a whole
 * @param detail what exactly was matched here (a class, field or key name)
 */
public record Finding(Rule rule, Path file, int line, String detail) implements Comparable<Finding> {

    @Override
    public int compareTo(Finding other) {
        int byCategory = rule.category().compareTo(other.rule.category());
        if (byCategory != 0) {
            return byCategory;
        }
        int byRule = rule.id().compareTo(other.rule.id());
        if (byRule != 0) {
            return byRule;
        }
        int byFile = file.toString().compareTo(other.file.toString());
        return byFile != 0 ? byFile : Integer.compare(line, other.line);
    }
}

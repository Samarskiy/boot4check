package dev.boot4check;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Checks application*.properties and application*.yml for keys that Spring Boot 4 silently ignores. */
final class ConfigScanner {

    static final String JACKSON2_DEFAULTS_KEY = "spring.jackson.use-jackson2-defaults";

    private static final List<String> RENAMED_PREFIXES = List.of(
            "spring.jackson.read.", "spring.jackson.write.", "spring.jackson.parser.");

    private final List<Finding> findings = new ArrayList<>();
    private boolean jackson2Defaults;

    /** True when the project opted back into Jackson 2 defaults, which mutes the default-change rules. */
    boolean usesJackson2Defaults() {
        return jackson2Defaults;
    }

    List<Finding> findings() {
        return findings;
    }

    void addProperties(Path relativeFile, List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int separator = firstSeparator(line);
            String key = (separator < 0 ? line : line.substring(0, separator)).strip();
            String value = separator < 0 ? "" : line.substring(separator + 1).strip();
            check(relativeFile, index + 1, key, value);
        }
    }

    void addYaml(Path relativeFile, String content) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        for (Node document : yaml.composeAll(new StringReader(content))) {
            walk(relativeFile, "", document);
        }
    }

    private void walk(Path file, String prefix, Node node) {
        if (!(node instanceof MappingNode mapping)) {
            return;
        }
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                continue;
            }
            String key = prefix.isEmpty() ? keyNode.getValue() : prefix + "." + keyNode.getValue();
            Node valueNode = tuple.getValueNode();
            if (valueNode instanceof ScalarNode scalar) {
                check(file, keyNode.getStartMark().getLine() + 1, key, scalar.getValue());
            } else {
                walk(file, key, valueNode);
            }
        }
    }

    private void check(Path file, int line, String rawKey, String value) {
        String key = canonical(rawKey);
        if (key.equals(JACKSON2_DEFAULTS_KEY) && value.equalsIgnoreCase("true")) {
            jackson2Defaults = true;
        }
        for (String prefix : RENAMED_PREFIXES) {
            if (key.startsWith(prefix)) {
                findings.add(new Finding(Rule.JACKSON_PROPERTY_RENAMED, file, line, rawKey));
            }
        }
    }

    /** Spring's relaxed binding: case and underscores do not matter. Dashes are kept for the known keys. */
    private static String canonical(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static int firstSeparator(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        return colon < 0 ? equals : Math.min(equals, colon);
    }
}

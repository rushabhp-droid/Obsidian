package com.obsidian.contract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads web-dashboard/src/lib/types.ts, the dashboard's view of the API, so
 * contract tests compare real responses against what the frontend declares.
 * Handles the subset that file uses: interfaces (with extends and inline
 * object properties) and string-literal union types.
 */
final class TypesTs {

    private static final Pattern PROPERTY = Pattern.compile("^\\s*(\\w+)\\??\\s*:");
    private static final Pattern LITERAL = Pattern.compile("\"([A-Z_]+)\"");

    private final String source;

    private TypesTs(String source) {
        this.source = source;
    }

    static TypesTs load() {
        try {
            return new TypesTs(Files.readString(Path.of("..", "web-dashboard", "src", "lib", "types.ts")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Top-level property names of an interface, including those it extends. */
    Set<String> fields(String name) {
        Matcher header = Pattern.compile("export interface " + name + "(?: extends (\\w+))? \\{").matcher(source);
        if (!header.find()) {
            throw new IllegalArgumentException("No interface " + name + " in types.ts");
        }
        Set<String> fields = new LinkedHashSet<>();
        if (header.group(1) != null) {
            fields.addAll(fields(header.group(1)));
        }
        fields.addAll(properties(header.end(), null));
        return fields;
    }

    /** Property names of an inline object type, e.g. SessionInfo.organization. */
    Set<String> nested(String name, String property) {
        Matcher header = Pattern.compile("export interface " + name + "(?: extends \\w+)? \\{").matcher(source);
        if (!header.find()) {
            throw new IllegalArgumentException("No interface " + name + " in types.ts");
        }
        return properties(header.end(), property);
    }

    /** Members of a string-literal union; referenced union types are expanded. */
    Set<String> union(String name) {
        Matcher type = Pattern.compile("export type " + name + " =([^;]+);").matcher(source);
        if (!type.find()) {
            throw new IllegalArgumentException("No type " + name + " in types.ts");
        }
        Set<String> members = new LinkedHashSet<>();
        for (String part : type.group(1).split("\\|")) {
            String token = part.trim();
            Matcher literal = LITERAL.matcher(token);
            if (literal.matches()) {
                members.add(literal.group(1));
            } else if (token.matches("\\w+")) {
                members.addAll(union(token));
            }
        }
        return members;
    }

    /** Scans the body that starts after an opening brace; collects depth-1 keys, or one property's depth-2 keys. */
    private Set<String> properties(int start, String insideProperty) {
        Set<String> keys = new LinkedHashSet<>();
        int depth = 1;
        String currentProperty = null;
        for (String line : source.substring(start).split("\n")) {
            Matcher property = PROPERTY.matcher(line);
            boolean declaresProperty = property.find() && !line.trim().startsWith("*") && !line.trim().startsWith("//");
            if (declaresProperty && depth == 1) {
                currentProperty = property.group(1);
                if (insideProperty == null) {
                    keys.add(currentProperty);
                }
            } else if (declaresProperty && depth == 2 && insideProperty != null && insideProperty.equals(currentProperty)) {
                keys.add(property.group(1));
            }
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (depth == 0) {
                break;
            }
        }
        return keys;
    }
}

package com.obsidian.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** .env.example lists every variable the code reads, in a form both Spring and docker compose parse. */
class EnvExampleTest {

    static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");

    static Set<String> references(Path file) throws IOException {
        Set<String> names = new TreeSet<>();
        Matcher matcher = REFERENCE.matcher(Files.readString(file));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    static List<String> settings() throws IOException {
        return Files.readAllLines(Path.of("..", ".env.example")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
    }

    @Test
    void listsEveryVariableSpringAndDockerComposeRead() throws IOException {
        Set<String> keys = new TreeSet<>(settings().stream().map(line -> line.substring(0, line.indexOf('='))).toList());

        assertThat(keys).containsAll(references(Path.of("src", "main", "resources", "application.yml")));
        assertThat(keys).containsAll(references(Path.of("..", "docker-compose.yml")));
        assertThat(keys).contains("TURNSTILE_SITE_KEY", "GITHUB_APP_ID", "GITHUB_APP_PRIVATE_KEY_PATH", "GITHUB_WEBHOOK_SECRET");
    }

    @Test
    void everySettingIsAPlainKeyValueLine() throws IOException {
        assertThat(settings()).allSatisfy(line -> assertThat(line)
                .matches("[A-Z][A-Z0-9_]*=[^\\s\"'#$]*")
                .as("no quotes, spaces, inline comments or substitutions: %s", line));
    }

    @Test
    void secretsAreBlankInTheExample() throws IOException {
        assertThat(settings()).filteredOn(line -> line.matches("(APP_ENC_KEY|VT_API_KEY|TURNSTILE_SECRET_KEY|GMAIL_CLIENT_SECRET|GITHUB_WEBHOOK_SECRET)=.*"))
                .allSatisfy(line -> assertThat(line).endsWith("="));
    }
}

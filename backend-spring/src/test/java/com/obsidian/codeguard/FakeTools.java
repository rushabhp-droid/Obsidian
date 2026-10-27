package com.obsidian.codeguard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Stand-ins for gitleaks and osv-scanner that write the real tools' report formats. They are run
 * by ToolRunner exactly like the real binaries: ProcessBuilder with an argument list.
 */
final class FakeTools {

    static final String SECRET = "AKIAQOBSIDIANDEMO7EXAMPLE";

    private FakeTools() {}

    /** Writes a gitleaks v8 JSON report to the --report-path it is given. */
    static Path gitleaks(Path dir) throws IOException {
        return script(dir.resolve("gitleaks"), """
                #!/usr/bin/env bash
                src=""; report=""
                while [ $# -gt 0 ]; do
                  case "$1" in --source) src=$2; shift ;; --report-path) report=$2; shift ;; esac; shift
                done
                [ -f "$src/config/settings.py" ] || { echo '[]' > "$report"; exit 0; }
                cat > "$report" <<JSON
                [{"Description":"AWS Access Key","StartLine":3,"EndLine":3,"StartColumn":14,"EndColumn":38,
                  "Match":"AWS_KEY = \\"%s\\"","Secret":"%s","File":"$src/config/settings.py",
                  "SymlinkFile":"","Commit":"","Entropy":3.9,"Author":"","Email":"","Date":"","Message":"",
                  "Tags":[],"RuleID":"aws-access-token","Fingerprint":"config/settings.py:aws-access-token:3"}]
                JSON
                exit 0
                """.formatted(SECRET, SECRET));
    }

    /** Prints an OSV-Scanner JSON report on stdout and exits 1 (vulnerabilities found). */
    static Path osvScanner(Path dir) throws IOException {
        return script(dir.resolve("osv-scanner"), """
                #!/usr/bin/env bash
                src="${@: -1}"
                cat <<JSON
                {"results":[{"source":{"path":"$src/package-lock.json","type":"lockfile"},"packages":[
                  {"package":{"name":"lodash","version":"4.17.15","ecosystem":"npm"},
                   "vulnerabilities":[{"id":"GHSA-p6mc-m468-83gw","summary":"Prototype Pollution in lodash"}],
                   "groups":[{"ids":["GHSA-p6mc-m468-83gw","CVE-2020-8203"],"aliases":["CVE-2020-8203"],"max_severity":"7.4"}]}]}]}
                JSON
                exit 1
                """);
    }

    private static Path script(Path file, String body) throws IOException {
        Files.writeString(file, body);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }
}

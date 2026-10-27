package com.obsidian.scanner;

import com.obsidian.model.AuthCheck;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPF / DKIM / DMARC from the topmost Authentication-Results header, and only
 * if mx.google.com wrote it. Gmail adds its header on top; anything below it
 * could have been written by the sender. Comments in parentheses are dropped
 * before reading results.
 */
public final class HeaderAuthValidator {

    public record AuthResults(AuthCheck spf, AuthCheck dkim, AuthCheck dmarc) {

        public boolean allPass() {
            return spf == AuthCheck.PASS && dkim == AuthCheck.PASS && dmarc == AuthCheck.PASS;
        }
    }

    private static final AuthResults NONE = new AuthResults(AuthCheck.NONE, AuthCheck.NONE, AuthCheck.NONE);
    private static final Pattern RESULT = Pattern.compile("(?i)\\b(spf|dkim|dmarc)\\s*=\\s*([a-z]+)");
    private static final Pattern COMMENT = Pattern.compile("\\([^()]*\\)");

    private HeaderAuthValidator() {}

    /** @param headers every Authentication-Results value, in header order (topmost first) */
    public static AuthResults parse(List<String> headers) {
        if (headers.isEmpty()) {
            return NONE;
        }
        String top = headers.get(0).replaceAll("\\s+", " ").trim();
        int semicolon = top.indexOf(';');
        String authservId = (semicolon < 0 ? top : top.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
        if (!authservId.equals("mx.google.com")) {
            return NONE;
        }
        String results = top.substring(semicolon + 1);
        String previous;
        do {
            previous = results;
            results = COMMENT.matcher(results).replaceAll(" ");
        } while (!results.equals(previous));

        AuthCheck spf = AuthCheck.NONE;
        AuthCheck dkim = AuthCheck.NONE;
        AuthCheck dmarc = AuthCheck.NONE;
        Matcher matcher = RESULT.matcher(results);
        while (matcher.find()) {
            AuthCheck check = toCheck(matcher.group(2));
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "spf" -> spf = combine(spf, check);
                case "dkim" -> dkim = combine(dkim, check);
                default -> dmarc = combine(dmarc, check);
            }
        }
        return new AuthResults(spf, dkim, dmarc);
    }

    private static AuthCheck toCheck(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "pass" -> AuthCheck.PASS;
            case "none" -> AuthCheck.NONE;
            default -> AuthCheck.FAIL; // fail, softfail, neutral, temperror, permerror, policy
        };
    }

    /** Several results of one method (e.g. two DKIM signatures): any pass passes. */
    private static AuthCheck combine(AuthCheck current, AuthCheck next) {
        if (current == AuthCheck.PASS || next == AuthCheck.PASS) {
            return AuthCheck.PASS;
        }
        return current == AuthCheck.FAIL || next == AuthCheck.FAIL ? AuthCheck.FAIL : AuthCheck.NONE;
    }
}

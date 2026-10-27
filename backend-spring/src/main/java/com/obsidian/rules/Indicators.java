package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalization and validation for every indicator type. The rules match
 * web-dashboard/src/lib/indicators.ts; values are lowercased before they are
 * stored or looked up (CLAUDE.md). Validation never resolves DNS.
 */
public final class Indicators {

    private static final Pattern DOMAIN =
            Pattern.compile("^(?=.{1,253}$)(?:(?!-)[a-z0-9-]{1,63}(?<!-)\\.)+[a-z]{2,63}$");
    private static final Pattern IPV4 = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern HEX_GROUP = Pattern.compile("^[0-9a-f]{1,4}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{6,15}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-()]");
    private static final int MAX_URL_LENGTH = 2048;

    /** A URL's host, typed as the indicator a rule for it would use. */
    public record Host(IndicatorType type, String value) {}

    private Indicators() {}

    public static String normalize(IndicatorType type, String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case PHONE -> PHONE_SEPARATORS.matcher(value).replaceAll("");
            case DOMAIN -> value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
            default -> value;
        };
    }

    /** An error message for the user, or empty when the normalized value is acceptable. */
    public static Optional<String> validate(IndicatorType type, String value) {
        if (value == null || value.isEmpty()) {
            return Optional.of("Enter a value.");
        }
        boolean ok = switch (type) {
            case DOMAIN -> DOMAIN.matcher(value).matches();
            case IP -> isIpAddress(value);
            case URL -> parseHttpUrl(value).isPresent();
            case EMAIL -> value.length() <= 254 && EMAIL.matcher(value).matches();
            case PHONE -> PHONE.matcher(value).matches();
            case FILE_HASH -> SHA256.matcher(value).matches();
        };
        return ok ? Optional.empty() : Optional.of(message(type));
    }

    /** Normalizes, validates and returns the stored form, or throws {@link InvalidIndicatorException}. */
    public static String requireValid(IndicatorType type, String raw) {
        String value = normalize(type, raw);
        validate(type, value).ifPresent(error -> {
            throw new InvalidIndicatorException(error);
        });
        return value;
    }

    public static boolean isNetwork(IndicatorType type) {
        return type == IndicatorType.DOMAIN || type == IndicatorType.IP;
    }

    /** The URL's host as a DOMAIN or IP indicator; empty when it has none we could rule on. */
    public static Optional<Host> hostOf(String url) {
        return parseHttpUrl(url == null ? "" : url.trim()).flatMap(uri -> {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            if (isIpAddress(host)) {
                return Optional.of(new Host(IndicatorType.IP, host));
            }
            String domain = normalize(IndicatorType.DOMAIN, host);
            return DOMAIN.matcher(domain).matches()
                    ? Optional.of(new Host(IndicatorType.DOMAIN, domain))
                    : Optional.empty();
        });
    }

    public static Optional<String> domainOfEmail(String address) {
        if (address == null) {
            return Optional.empty();
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return Optional.empty();
        }
        String domain = normalize(IndicatorType.DOMAIN, address.substring(at + 1));
        return DOMAIN.matcher(domain).matches() ? Optional.of(domain) : Optional.empty();
    }

    private static Optional<URI> parseHttpUrl(String value) {
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return http && uri.getHost() != null && !uri.getHost().isEmpty() ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    static boolean isIpAddress(String value) {
        return IPV4.matcher(value).matches() || isIpv6(value);
    }

    /** RFC 4291 text form: eight hex groups, one optional "::", optional trailing IPv4. No zone IDs. */
    private static boolean isIpv6(String value) {
        if (!value.contains(":") || value.contains("%") || value.contains(":::")) {
            return false;
        }
        int doubleColon = value.indexOf("::");
        if (doubleColon >= 0 && value.indexOf("::", doubleColon + 1) >= 0) {
            return false;
        }
        String[] halves = doubleColon >= 0
                ? new String[] {value.substring(0, doubleColon), value.substring(doubleColon + 2)}
                : new String[] {value};
        int groups = 0;
        for (int h = 0; h < halves.length; h++) {
            if (halves[h].isEmpty()) {
                continue;
            }
            String[] parts = halves[h].split(":", -1);
            for (int i = 0; i < parts.length; i++) {
                boolean last = h == halves.length - 1 && i == parts.length - 1;
                if (last && parts[i].contains(".")) {
                    if (!IPV4.matcher(parts[i]).matches()) {
                        return false;
                    }
                    groups += 2;
                } else if (HEX_GROUP.matcher(parts[i]).matches()) {
                    groups++;
                } else {
                    return false;
                }
            }
        }
        return doubleColon >= 0 ? groups < 8 : groups == 8;
    }

    private static String message(IndicatorType type) {
        return switch (type) {
            case DOMAIN -> "Enter a domain such as login-verify.example, without a scheme or path.";
            case IP -> "Enter a single IPv4 or IPv6 address, without a port or range.";
            case URL -> "Enter a full http:// or https:// URL.";
            case EMAIL -> "Enter a sender address such as billing@invoice-verify.example.";
            case PHONE -> "Enter a phone number with country code, digits only, such as +15550100.";
            case FILE_HASH -> "Enter a SHA-256 hash: 64 hexadecimal characters.";
        };
    }
}

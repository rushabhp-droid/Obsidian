package com.obsidian.scanner;

import com.obsidian.model.IndicatorType;
import com.obsidian.rules.Indicators;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Links in message text, and clean-up of links clients send (punctuation, missing scheme). */
public final class UrlExtractor {

    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"'`]+");
    private static final String TRAILING = ".,;:!?)]}>'\"";
    private static final String LEADING = "([{<'\"";

    private UrlExtractor() {}

    public static List<String> extract(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            clean(matcher.group()).ifPresent(links::add);
        }
        return new ArrayList<>(links);
    }

    /** The link as it should be scanned (case kept for VirusTotal), or empty if it isn't an http(s) URL. */
    public static Optional<String> clean(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String link = candidate.trim();
        while (!link.isEmpty() && LEADING.indexOf(link.charAt(0)) >= 0) {
            link = link.substring(1);
        }
        while (!link.isEmpty() && TRAILING.indexOf(link.charAt(link.length() - 1)) >= 0) {
            link = link.substring(0, link.length() - 1);
        }
        if (link.regionMatches(true, 0, "www.", 0, 4)) {
            link = "http://" + link;
        }
        String normalized = Indicators.normalize(IndicatorType.URL, link);
        return Indicators.validate(IndicatorType.URL, normalized).isEmpty() ? Optional.of(link) : Optional.empty();
    }
}

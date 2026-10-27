package com.obsidian.mobile.intercept

import java.util.regex.Pattern

object UrlExtractor {

    // Regex pattern matching URLs with or without http(s) protocol
    private val URL_PATTERN = Pattern.compile(
        "\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))",
        Pattern.CASE_INSENSITIVE
    )

    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()

        val urls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            var url = matcher.group(1) ?: continue
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            urls.add(url)
        }
        return urls.distinct()
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            url.split("/").getOrNull(0) ?: url
        }
    }
}

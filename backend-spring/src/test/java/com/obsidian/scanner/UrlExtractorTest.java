package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlExtractorTest {

    @Test
    void findsHttpAndWwwLinksInMessageText() {
        assertThat(UrlExtractor.extract("Your parcel is held: http://delivery-status.example/track/88231. "
                + "Or visit www.parcel-help.example/pay, then reply STOP"))
                .containsExactly("http://delivery-status.example/track/88231", "http://www.parcel-help.example/pay");
    }

    @Test
    void dedupesRepeatedLinks() {
        assertThat(UrlExtractor.extract("https://a.example/x and again https://a.example/x")).containsExactly("https://a.example/x");
    }

    @Test
    void textWithoutLinksHasNone() {
        assertThat(UrlExtractor.extract("See you at 6? Bring the charger.")).isEmpty();
        assertThat(UrlExtractor.extract(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "'(https://a.example/x)'          | https://a.example/x",
        "'https://a.example/x,'           | https://a.example/x",
        "'www.a.example'                  | http://www.a.example",
        "'  https://a.example/path?q=1 '  | https://a.example/path?q=1",
    })
    void cleansCandidatesFromClients(String raw, String clean) {
        assertThat(UrlExtractor.clean(raw)).contains(clean);
    }

    @Test
    void dropsCandidatesThatAreNotHttpUrls() {
        assertThat(UrlExtractor.clean("javascript:alert(1)")).isEmpty();
        assertThat(UrlExtractor.clean("not a link")).isEmpty();
    }
}

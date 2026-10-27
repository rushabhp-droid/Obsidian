package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.model.AuthCheck;
import com.obsidian.scanner.HeaderAuthValidator.AuthResults;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Only the topmost Authentication-Results header, and only when mx.google.com wrote it (CLAUDE.md). */
class HeaderAuthValidatorTest {

    static final String GOOGLE_PASS = "mx.google.com; dkim=pass header.i=@github.com; "
            + "spf=pass (google.com: domain of x designates 1.2.3.4) smtp.mailfrom=x@github.com; "
            + "dmarc=pass (p=REJECT) header.from=github.com";

    @Test
    void readsAllThreeResultsFromGoogle() {
        assertThat(HeaderAuthValidator.parse(List.of(GOOGLE_PASS)))
                .isEqualTo(new AuthResults(AuthCheck.PASS, AuthCheck.PASS, AuthCheck.PASS));
    }

    @Test
    void forgedHeadersBelowGooglesAreIgnored() {
        String google = "mx.google.com; spf=fail smtp.mailfrom=x@evil.example; dmarc=fail header.from=evil.example";
        String forged = "mx.google.com; spf=pass dkim=pass dmarc=pass";

        assertThat(HeaderAuthValidator.parse(List.of(google, forged)))
                .isEqualTo(new AuthResults(AuthCheck.FAIL, AuthCheck.NONE, AuthCheck.FAIL));
    }

    @Test
    void topmostHeaderFromAnotherServerMeansNoResults() {
        assertThat(HeaderAuthValidator.parse(List.of("evil.example; spf=pass dkim=pass dmarc=pass", GOOGLE_PASS)))
                .isEqualTo(new AuthResults(AuthCheck.NONE, AuthCheck.NONE, AuthCheck.NONE));
    }

    @Test
    void resultWordsInsideCommentsDontCount() {
        String header = "mx.google.com; spf=softfail (dkim=pass dmarc=pass looks legit) smtp.mailfrom=x@evil.example";

        assertThat(HeaderAuthValidator.parse(List.of(header)))
                .isEqualTo(new AuthResults(AuthCheck.FAIL, AuthCheck.NONE, AuthCheck.NONE));
    }

    @Test
    void oneGoodDkimSignatureIsAPass() {
        String header = "mx.google.com; dkim=fail header.i=@old.example; dkim=pass header.i=@github.com; spf=none; dmarc=pass";

        assertThat(HeaderAuthValidator.parse(List.of(header)))
                .isEqualTo(new AuthResults(AuthCheck.NONE, AuthCheck.PASS, AuthCheck.PASS));
    }

    @Test
    void noHeaderMeansNoResults() {
        assertThat(HeaderAuthValidator.parse(List.of()))
                .isEqualTo(new AuthResults(AuthCheck.NONE, AuthCheck.NONE, AuthCheck.NONE));
    }
}

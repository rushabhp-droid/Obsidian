package com.obsidian.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.obsidian.model.IndicatorType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class IndicatorsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DOMAIN    | ' Login-Verify.EXAMPLE '  | login-verify.example",
        "DOMAIN    | 'docs.aster.example.'      | docs.aster.example",
        "PHONE     | '+1 (555) 010-0'           | +15550100",
        "EMAIL     | 'Billing@Invoice-Verify.example' | billing@invoice-verify.example",
        "URL       | 'HTTP://Login-Verify.example/Reset' | http://login-verify.example/reset",
        "FILE_HASH | '5F2B8A0C94D1E3B7A6C9F0E1D2C3B4A5968778695A4B3C2D1E0F9A8B7C6D5E4F' | 5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
    })
    void normalizesToTrimmedLowercase(IndicatorType type, String raw, String expected) {
        assertThat(Indicators.normalize(type, raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DOMAIN    | login-verify.example",
        "DOMAIN    | testsafebrowsing.appspot.com",
        "IP        | 203.0.113.45",
        "IP        | 2001:db8::1",
        "IP        | ::1",
        "IP        | ::ffff:192.0.2.1",
        "IP        | 2001:db8:0:0:0:0:0:1",
        "URL       | https://login-verify.example/reset?x=1",
        "URL       | http://203.0.113.45/payload",
        "EMAIL     | billing@invoice-verify.example",
        "PHONE     | +15550100",
        "PHONE     | 5550100",
        "FILE_HASH | 5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
    })
    void acceptsWellFormedValues(IndicatorType type, String value) {
        assertThat(Indicators.validate(type, value)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DOMAIN    | localhost",
        "DOMAIN    | -bad.example",
        "DOMAIN    | bad-.example",
        "DOMAIN    | evil.example/path",
        "DOMAIN    | http://evil.example",
        "DOMAIN    | '*.evil.example'",
        "DOMAIN    | 'evil.example # OBSIDIAN-MANAGED'",
        "DOMAIN    | 203.0.113.45",
        "IP        | 256.1.1.1",
        "IP        | 1.2.3",
        "IP        | 203.0.113.45/24",
        "IP        | 203.0.113.45:80",
        "IP        | 2001:db8::1%eth0",
        "IP        | fe80::1::2",
        "IP        | 2001:db8:0:0:0:0:0:0:1",
        "IP        | 12345::1",
        "IP        | evil.example",
        "URL       | ftp://files.example/x",
        "URL       | javascript:alert(1)",
        "URL       | login-verify.example/reset",
        "URL       | http://",
        "URL       | 'http://exa mple.com/'",
        "EMAIL     | billing@",
        "EMAIL     | 'a b@c.example'",
        "PHONE     | +1555",
        "PHONE     | call-me",
        "FILE_HASH | 5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4",
        "FILE_HASH | zz2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
    })
    void rejectsMalformedValues(IndicatorType type, String value) {
        assertThat(Indicators.validate(type, value)).isPresent();
    }

    @Test
    void rejectsNewlineInjectionIntoHostsLines() {
        assertThat(Indicators.validate(IndicatorType.DOMAIN, "evil.example\n0.0.0.0 bank.example")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlank(String raw) {
        assertThatThrownBy(() -> Indicators.requireValid(IndicatorType.DOMAIN, raw))
                .isInstanceOf(InvalidIndicatorException.class);
    }

    @Test
    void requireValidReturnsTheNormalizedValue() {
        assertThat(Indicators.requireValid(IndicatorType.DOMAIN, " Login-Verify.EXAMPLE ")).isEqualTo("login-verify.example");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://Login.Example/x          | DOMAIN | login.example",
        "http://203.0.113.45/payload      | IP     | 203.0.113.45",
        "http://[2001:db8::1]:8080/a      | IP     | 2001:db8::1",
    })
    void hostOfUrlIsTypedAsDomainOrIp(String url, IndicatorType type, String host) {
        assertThat(Indicators.hostOf(url)).contains(new Indicators.Host(type, host));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "mailto:a@b.example", "http://localhost/x"})
    void hostOfIgnoresUrlsWithoutAUsableHost(String url) {
        assertThat(Indicators.hostOf(url)).isEmpty();
    }

    @Test
    void domainOfEmailIsTheLowercasePartAfterTheAt() {
        assertThat(Indicators.domainOfEmail("Billing@Invoice-Verify.example")).contains("invoice-verify.example");
        assertThat(Indicators.domainOfEmail("no-at-sign")).isEmpty();
    }
}

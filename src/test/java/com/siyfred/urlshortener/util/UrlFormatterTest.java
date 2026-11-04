package com.siyfred.urlshortener.util;

import com.siyfred.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlFormatterTest {
    private final UrlFormatter formatter = new UrlFormatter();

    @Test
    void addsHttpsWhenSchemeMissing() {
        assertThat(formatter.formatUrl(" google.com ")).isEqualTo("https://google.com");
        assertThat(formatter.formatUrl("http://google.com/a?b=c#d")).isEqualTo("http://google.com/a?b=c#d");
        assertThat(formatter.formatUrl("https://google.com:8443/x")).isEqualTo("https://google.com:8443/x");
    }

    @Test
    void convertsInternationalDomainsToPunycode() {
        assertThat(formatter.formatUrl("bücher.de")).isEqualTo("https://xn--bcher-kva.de");
        assertThat(formatter.formatUrl("https://пример.рф/путь")).isEqualTo("https://xn--e1afmkfd.xn--p1ai/путь");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "google", "https://google", "", "   ", "ftp://google.com", "javascript:alert(1)",
            "https://google.com:99999", "https://google.com:0", "https://google.com:",
            "localhost", "http://localhost:8080", "127.0.0.1", "10.0.0.1", "192.168.1.1",
            "172.16.0.1", "169.254.169.254", "0.0.0.0", "http://[::1]/", "999.1.1.1",
            "https://-bad-.com", "https://goo gle.com", "https://google.c", "https://google.123",
            "https://.com", "https://a..com"
    })
    void rejectsInvalidUrls(String url) {
        assertThatThrownBy(() -> formatter.formatUrl(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> formatter.formatUrl(null)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsPublicIp() {
        assertThat(formatter.formatUrl("8.8.8.8")).isEqualTo("https://8.8.8.8");
    }
}

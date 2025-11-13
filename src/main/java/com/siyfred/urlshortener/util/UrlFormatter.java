package com.siyfred.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class UrlFormatter {
    public String formatUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL não pode ser vazia.");
        }
        String trimmedUrl = url.trim();
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl;
        }
        return "https://" + trimmedUrl;
    }
}
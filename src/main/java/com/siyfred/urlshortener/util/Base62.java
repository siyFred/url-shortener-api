package com.siyfred.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62 {
    private static final String BASE_62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public String encode(long id) {
        if(id == 0) return "0";

        StringBuilder shortUrl = new StringBuilder();
        while(id > 0) {
            shortUrl.append(BASE_62_CHARS.charAt((int)(id % 62)));
            id /= 62;
        }
        return shortUrl.reverse().toString();
    }
}

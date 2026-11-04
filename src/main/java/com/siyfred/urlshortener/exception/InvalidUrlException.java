package com.siyfred.urlshortener.exception;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}

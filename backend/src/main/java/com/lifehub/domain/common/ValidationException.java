package com.lifehub.domain.common;

/** Input violates a business rule. Maps to {@code VALIDATION_ERROR} / HTTP 400. */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, String field) {
        super(message, field);
    }
}

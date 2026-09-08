package com.lifehub.domain.common;

/** Business constraint violated, e.g. duplicate name. Maps to {@code CONFLICT} / HTTP 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, String field) {
        super(message, field);
    }
}

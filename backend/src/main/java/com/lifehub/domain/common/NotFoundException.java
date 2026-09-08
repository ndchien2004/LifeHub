package com.lifehub.domain.common;

/** Requested record does not exist or has been soft deleted. Maps to {@code NOT_FOUND} / HTTP 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}

package com.lifehub.domain.common;

/**
 * Base type for every business rule violation raised by the domain layer.
 *
 * <p>The domain layer never references HTTP concepts. {@code GlobalExceptionHandler} in the api
 * layer is responsible for translating each subtype into an {@code ErrorCode} and status.
 */
public abstract class DomainException extends RuntimeException {

    private final String field;

    protected DomainException(String message) {
        this(message, null);
    }

    protected DomainException(String message, String field) {
        super(message);
        this.field = field;
    }

    /** Name of the offending input field, or {@code null} when the rule is not field specific. */
    public String getField() {
        return field;
    }
}

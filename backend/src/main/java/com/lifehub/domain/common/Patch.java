package com.lifehub.domain.common;

import java.util.function.Consumer;

/**
 * A field of a partial update that may be absent, or present with a possibly null value.
 *
 * <p>PATCH has to distinguish "leave this alone" from "set this to null" - clearing a due date and
 * not touching it are different intentions that a bare null cannot express. Every update command
 * in the application layer wraps its members in this.
 *
 * @param present whether the caller mentioned the field at all
 * @param value the value the caller sent, which may legitimately be null
 */
public record Patch<T>(boolean present, T value) {

    private static final Patch<?> ABSENT = new Patch<>(false, null);

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() {
        return (Patch<T>) ABSENT;
    }

    public static <T> Patch<T> of(T value) {
        return new Patch<>(true, value);
    }

    public void ifPresent(Consumer<T> action) {
        if (present) {
            action.accept(value);
        }
    }

    /** The value if present, otherwise {@code fallback}. */
    public T orElse(T fallback) {
        return present ? value : fallback;
    }
}

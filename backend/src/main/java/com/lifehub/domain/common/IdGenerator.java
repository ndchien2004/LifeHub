package com.lifehub.domain.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

/**
 * Central factory for entity identifiers.
 *
 * <p>Every entity in LifeHub uses a UUID v7 rendered as a string (AGENTS.md 3.2). UUID v7 embeds a
 * Unix millisecond timestamp in its most significant bits, so identifiers generated in different
 * milliseconds sort chronologically as plain strings. Ordering of two identifiers created inside the
 * <em>same</em> millisecond is not defined - the trailing bits are random.
 */
public final class IdGenerator {

    private static final NoArgGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

    private IdGenerator() {
    }

    public static String newId() {
        return UUID_V7.generate().toString();
    }
}

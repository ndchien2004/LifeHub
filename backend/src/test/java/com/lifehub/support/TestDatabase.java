package com.lifehub.support;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hands each integration test class its own private SQLite file.
 *
 * <p>08-TEST-PLAN.md §2 requires that test classes not share state. A shared in-memory database
 * ({@code file::memory:?cache=shared}) is one database per JVM, so it cannot satisfy that. A real
 * file per class does, and has the bonus of exercising the same WAL and pragma code path that
 * production uses.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** A datasource URL pointing at a fresh temporary database, with the mandatory pragmas applied. */
    public static String freshUrl() {
        try {
            Path directory = Files.createTempDirectory("lifehub-test-");
            directory.toFile().deleteOnExit();
            Path database = directory.resolve("lifehub-test.db");
            database.toFile().deleteOnExit();
            // Forward slashes: the driver treats a backslash before a query string inconsistently on Windows.
            String path = database.toAbsolutePath().toString().replace(File.separatorChar, '/');
            return "jdbc:sqlite:" + path
                    + "?journal_mode=WAL&foreign_keys=true&busy_timeout=5000&synchronous=NORMAL";
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được database tạm cho test", e);
        }
    }
}

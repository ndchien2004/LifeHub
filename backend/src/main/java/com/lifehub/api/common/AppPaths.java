package com.lifehub.api.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolves and creates the writable directories the application needs.
 *
 * <p>This runs before the Spring context exists. The SQLite driver creates a missing database file
 * but not a missing parent directory, and Flyway opens its connection during context startup - so by
 * the time any Spring managed code could react, the failure has already happened.
 */
public final class AppPaths {

    public static final String DEFAULT_DATA_DIR = "data";
    public static final String DEFAULT_LOG_DIR = "logs";

    private AppPaths() {
    }

    /**
     * Reads {@code --app.data-dir} / {@code --app.log-dir} from the command line, falling back to the
     * {@code LIFEHUB_DATA_DIR} / {@code LIFEHUB_LOG_DIR} environment variables and then to the
     * defaults, and creates both directories.
     */
    public static void prepareDirectories(String[] args, Map<String, String> environment) {
        createDirectory(resolve(args, environment, "app.data-dir", "LIFEHUB_DATA_DIR", DEFAULT_DATA_DIR));
        createDirectory(resolve(args, environment, "app.log-dir", "LIFEHUB_LOG_DIR", DEFAULT_LOG_DIR));
    }

    static String resolve(String[] args, Map<String, String> environment, String argName, String envName, String fallback) {
        String prefix = "--" + argName + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length());
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        String fromEnv = environment.get(envName);
        return fromEnv == null || fromEnv.isBlank() ? fallback : fromEnv;
    }

    private static void createDirectory(String directory) {
        try {
            Files.createDirectories(Path.of(directory));
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục " + directory, e);
        }
    }
}

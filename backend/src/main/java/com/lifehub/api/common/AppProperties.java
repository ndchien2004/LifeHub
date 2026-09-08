package com.lifehub.api.common;

import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration handed to the backend by the Electron shell at spawn time.
 *
 * <p>In normal operation Electron supplies all three values on the command line. When the jar is
 * started by hand - during development or from a UAT script - no token is present, so one is
 * generated and logged. That keeps the endpoint closed by default: a caller who has not read the log
 * cannot guess the value, and nothing ever runs with an empty token.
 *
 * @param token shared secret required on every {@code /api/v1} request (NFR-SEC-04)
 * @param dataDir directory holding {@code lifehub.db} and {@code backups/}
 * @param logDir directory holding rotated log files
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String token, String dataDir, String logDir) {

    private static final Logger log = LoggerFactory.getLogger(AppProperties.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public AppProperties {
        if (isBlank(token)) {
            token = randomToken();
            log.warn("app.token was not supplied, generated one for this run: {}", token);
            log.warn("Send it as the {} header to call /api/v1. Electron normally provides this.",
                    AppTokenFilter.HEADER);
        }
        dataDir = isBlank(dataDir) ? AppPaths.DEFAULT_DATA_DIR : dataDir;
        logDir = isBlank(logDir) ? AppPaths.DEFAULT_LOG_DIR : logDir;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

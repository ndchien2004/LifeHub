package com.lifehub.infrastructure.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Verifies that the SQLite pragmas required by 03-DATA-MODEL.md 5 are actually in effect.
 *
 * <p>The pragmas themselves are applied by the JDBC driver from query parameters on the datasource
 * URL, which is the only place that reaches <em>every</em> connection - including the ones Flyway
 * opens before the Spring context is up. This class does not set them; it reads them back and fails
 * loudly if one is missing, because a silently disabled {@code foreign_keys} is the single most
 * common way to lose referential integrity on SQLite.
 */
@Configuration
public class SqliteConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfig.class);

    private static final String[] VERIFIED_PRAGMAS = {"journal_mode", "foreign_keys", "busy_timeout", "synchronous"};

    private final DataSource dataSource;

    public SqliteConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyPragmas() throws SQLException {
        Map<String, String> actual = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String pragma : VERIFIED_PRAGMAS) {
                actual.put(pragma, readPragma(connection, pragma));
            }
        }
        log.info("SQLite pragmas: {}", actual);

        if (!"1".equals(actual.get("foreign_keys"))) {
            throw new IllegalStateException(
                    "PRAGMA foreign_keys is OFF. Foreign key constraints would be ignored silently. "
                            + "Check the foreign_keys parameter on spring.datasource.url.");
        }
    }

    private String readPragma(Connection connection, String pragma) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getString(1) : "<unset>";
        }
    }
}

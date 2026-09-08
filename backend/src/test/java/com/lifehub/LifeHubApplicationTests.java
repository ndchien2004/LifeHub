package com.lifehub;

import com.lifehub.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** T0-01 — the Spring context starts with no exception. */
@SpringBootTest
@ActiveProfiles("test")
class LifeHubApplicationTests {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Test
    void contextLoads() {
        // Assertion is the absence of a startup failure; SqliteConfig also verifies the pragmas here.
    }
}

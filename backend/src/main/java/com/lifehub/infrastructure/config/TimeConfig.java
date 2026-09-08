package com.lifehub.infrastructure.config;

import com.lifehub.application.system.SettingService;
import java.time.Clock;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time related beans.
 *
 * <p>Two distinct concerns. The {@link Clock} is what services read "now" from, injected so tests
 * can fix it instead of racing the wall clock (08-TEST-PLAN.md §2); it is always UTC, matching how
 * timestamps are stored (AGENTS.md §3.2). The display zone is only used when rendering a timestamp
 * into an API response, so the frontend receives the offset form the spec shows
 * ({@code 2026-09-04T14:30:00+07:00}) rather than a bare Z.
 */
@Configuration
public class TimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Resolved once at startup from the {@code app.timezone} setting.
     *
     * <p>Reading it per request would mean a database round trip on every response mapping. The
     * Settings screen in Phase 4 will need to invalidate this, which is noted in PROGRESS.md.
     */
    @Bean
    public ZoneId displayZone(SettingService settingService) {
        String configured = settingService.get("app.timezone", FALLBACK_ZONE);
        try {
            return ZoneId.of(configured);
        } catch (Exception e) {
            log.warn("Timezone '{}' không hợp lệ, dùng {} thay thế", configured, FALLBACK_ZONE);
            return ZoneId.of(FALLBACK_ZONE);
        }
    }
}

package com.lifehub.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.application.ai.AiLogService;
import com.lifehub.domain.ai.AiParseLog;
import com.lifehub.domain.ai.AiParseLogRepository;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.support.TestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 03-DATA-MODEL.md 2.10 — the 90 day retention policy on the AI call log. */
@SpringBootTest
@ActiveProfiles("test")
class AiParseLogPurgerIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private AiParseLogRepository repository;

    @Autowired
    private AiLogService logService;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Xóa bản ghi cũ hơn 90 ngày, giữ nguyên phần còn lại")
    void deletesOnlyExpiredRows() {
        Instant now = Instant.now(clock);

        repository.save(entry(now.minus(Duration.ofDays(91))));
        repository.save(entry(now.minus(Duration.ofDays(200))));
        AiParseLog kept = repository.save(entry(now.minus(Duration.ofDays(89))));
        AiParseLog fresh = repository.save(entry(now));

        int removed = logService.purgeExpired();

        assertThat(removed).isEqualTo(2);
        assertThat(repository.findRecent(PageRequest.of(0, 50)).items())
                .extracting(AiParseLog::getId)
                .containsExactlyInAnyOrder(kept.getId(), fresh.getId());
    }

    @Test
    @DisplayName("Nhật ký trống thì dọn dẹp không làm gì và không lỗi")
    void doesNothingWhenTheLogIsEmpty() {
        logService.purgeExpired();

        assertThat(logService.purgeExpired()).isZero();
    }

    private AiParseLog entry(Instant createdAt) {
        return AiParseLog.started(AiRequestType.NL_PARSE, "cà phê 45k", createdAt)
                .succeeded(ParseIntent.TRANSACTION, "{}", "claude-opus-5", 420, 100, 30);
    }
}

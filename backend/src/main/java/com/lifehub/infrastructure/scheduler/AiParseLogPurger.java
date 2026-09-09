package com.lifehub.infrastructure.scheduler;

import com.lifehub.application.ai.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enforces the 90 day retention policy on {@code ai_parse_log} (03-DATA-MODEL.md 2.10).
 *
 * <p>Runs at startup rather than on a timer, which is what the policy says and what suits a desktop
 * app: it spends most of its life closed, so opening it is the reliable moment to do housekeeping.
 * A failure here is logged and ignored - tidying up old debug rows is never a reason to refuse to
 * start.
 */
@Component
public class AiParseLogPurger {

    private static final Logger log = LoggerFactory.getLogger(AiParseLogPurger.class);

    private final AiLogService logService;

    public AiParseLogPurger(AiLogService logService) {
        this.logService = logService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void purgeOnStartup() {
        try {
            int removed = logService.purgeExpired();
            if (removed > 0) {
                log.info("Đã xóa {} bản ghi ai_parse_log quá {} ngày",
                        removed, AiLogService.RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            log.warn("Không dọn được ai_parse_log cũ", e);
        }
    }
}

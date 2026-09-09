package com.lifehub.application.ai;

import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiParseLog;
import com.lifehub.domain.ai.AiParseLogRepository;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the AI call log (FR-AI-10).
 *
 * <p>Every write runs in its own transaction ({@link Propagation#REQUIRES_NEW}) and swallows its own
 * failures. The log exists to explain what happened to a request; it must never be the reason a
 * request fails. A parse that produced a perfectly good draft is not going to be thrown away because
 * the audit row would not save.
 */
@Service
public class AiLogService {

    private static final Logger log = LoggerFactory.getLogger(AiLogService.class);

    /** 03-DATA-MODEL.md 2.10: rows older than this are deleted when the app starts. */
    public static final Duration RETENTION = Duration.ofDays(90);

    private final AiParseLogRepository repository;
    private final Clock clock;

    public AiLogService(AiParseLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records a call that produced a usable answer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            AiRequestType type,
            String inputText,
            ParseIntent intent,
            String outputJson,
            String model,
            long latencyMs,
            int inputTokens,
            int outputTokens) {

        save(AiParseLog.started(type, inputText, Instant.now(clock))
                .succeeded(intent, outputJson, model, latencyMs, inputTokens, outputTokens));
    }

    /**
     * Records a call that fell back.
     *
     * @param errorCode why the AI path was not used, or null when AI was simply switched off
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            AiRequestType type,
            String inputText,
            AiErrorCode errorCode,
            String model,
            long latencyMs) {

        AiParseLog entry = AiParseLog.started(type, inputText, Instant.now(clock));
        if (errorCode == null) {
            // AI off is not a fault; the row still exists so every call is accounted for.
            save(entry);
            return;
        }
        save(entry.failed(errorCode, model, latencyMs));
    }

    @Transactional(readOnly = true)
    public Page<AiParseLog> findRecent(PageRequest pageRequest) {
        return repository.findRecent(pageRequest);
    }

    /** Deletes rows past the retention window; returns how many went. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteOlderThan(Instant.now(clock).minus(RETENTION));
    }

    private void save(AiParseLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Không ghi được ai_parse_log", e);
        }
    }
}

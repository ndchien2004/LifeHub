package com.lifehub.domain.ai;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import java.time.Instant;

/** Persistence port for the AI call log. */
public interface AiParseLogRepository {

    AiParseLog save(AiParseLog log);

    /** Most recent first, which is the only order the debug screen ever wants. */
    Page<AiParseLog> findRecent(PageRequest pageRequest);

    /** Deletes rows created before {@code cutoff}; returns how many went (03-DATA-MODEL.md 2.10). */
    int deleteOlderThan(Instant cutoff);
}

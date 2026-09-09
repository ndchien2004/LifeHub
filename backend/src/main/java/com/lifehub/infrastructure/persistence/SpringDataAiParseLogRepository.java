package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.ai.AiParseLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing. Application code depends on the domain port, never on this interface. */
public interface SpringDataAiParseLogRepository extends JpaRepository<AiParseLog, String> {

    @Modifying
    @Query("delete from AiParseLog l where l.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}

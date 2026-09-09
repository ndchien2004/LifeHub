package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.ai.AiParseLog;
import com.lifehub.domain.ai.AiParseLogRepository;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link AiParseLogRepository} port declared by the domain layer. */
@Repository
public class AiParseLogRepositoryAdapter implements AiParseLogRepository {

    private final SpringDataAiParseLogRepository delegate;

    public AiParseLogRepositoryAdapter(SpringDataAiParseLogRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AiParseLog save(AiParseLog log) {
        return delegate.save(log);
    }

    @Override
    public Page<AiParseLog> findRecent(PageRequest pageRequest) {
        org.springframework.data.domain.Page<AiParseLog> page = delegate.findAll(
                org.springframework.data.domain.PageRequest.of(
                        pageRequest.page(),
                        pageRequest.size(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                page.getContent(), pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return delegate.deleteByCreatedAtBefore(cutoff);
    }
}

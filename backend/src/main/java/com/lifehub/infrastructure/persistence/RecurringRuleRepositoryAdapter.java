package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.system.RecurringRule;
import com.lifehub.domain.system.RecurringRuleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link RecurringRuleRepository} port. */
@Repository
public class RecurringRuleRepositoryAdapter implements RecurringRuleRepository {

    private final SpringDataRecurringRuleRepository delegate;

    public RecurringRuleRepositoryAdapter(SpringDataRecurringRuleRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public RecurringRule save(RecurringRule rule) {
        return delegate.save(rule);
    }

    @Override
    public Optional<RecurringRule> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<RecurringRule> findAll() {
        return delegate.findAllOrdered();
    }

    @Override
    public List<RecurringRule> findDue(LocalDate today) {
        return delegate.findDue(today);
    }

    @Override
    public void delete(RecurringRule rule) {
        delegate.delete(rule);
    }
}

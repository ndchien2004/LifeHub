package com.lifehub.domain.system;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port for recurring transaction rules. */
public interface RecurringRuleRepository {

    RecurringRule save(RecurringRule rule);

    Optional<RecurringRule> findById(String id);

    List<RecurringRule> findAll();

    /** Active rules whose next run date has arrived (FR-FIN-13). */
    List<RecurringRule> findDue(LocalDate today);

    void delete(RecurringRule rule);
}

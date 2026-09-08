package com.lifehub.domain.finance;

import java.util.List;
import java.util.Optional;

/** Persistence port for budgets. */
public interface BudgetRepository {

    Budget save(Budget budget);

    Optional<Budget> findById(String id);

    /** Live budgets, newest first. */
    List<Budget> findAll(boolean includeInactive);

    /** Active budgets covering any of these categories, used when a transaction lands (SD-01). */
    List<Budget> findActiveByCategoryIds(List<String> categoryIds);

    boolean existsForCategoryAndPeriod(String categoryId, BudgetPeriod period, String excludingId);
}

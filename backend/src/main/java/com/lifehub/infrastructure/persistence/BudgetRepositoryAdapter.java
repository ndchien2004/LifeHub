package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Budget;
import com.lifehub.domain.finance.BudgetPeriod;
import com.lifehub.domain.finance.BudgetRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link BudgetRepository} port. */
@Repository
public class BudgetRepositoryAdapter implements BudgetRepository {

    private final SpringDataBudgetRepository delegate;

    public BudgetRepositoryAdapter(SpringDataBudgetRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Budget save(Budget budget) {
        return delegate.save(budget);
    }

    @Override
    public Optional<Budget> findById(String id) {
        return delegate.findLiveById(id).filter(budget -> !budget.isDeleted());
    }

    @Override
    public List<Budget> findAll(boolean includeInactive) {
        return includeInactive ? delegate.findAllLive() : delegate.findAllActive();
    }

    @Override
    public List<Budget> findActiveByCategoryIds(List<String> categoryIds) {
        return categoryIds == null || categoryIds.isEmpty()
                ? List.of()
                : delegate.findActiveByCategoryIds(categoryIds.stream().filter(id -> id != null).toList());
    }

    @Override
    public boolean existsForCategoryAndPeriod(String categoryId, BudgetPeriod period, String excludingId) {
        return delegate.existsForCategoryAndPeriod(categoryId, period, excludingId);
    }
}

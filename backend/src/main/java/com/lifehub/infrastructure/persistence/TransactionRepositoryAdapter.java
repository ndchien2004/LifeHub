package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.TransactionType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link TransactionRepository} port. */
@Repository
public class TransactionRepositoryAdapter implements TransactionRepository {

    /** Allow list: sorting on an arbitrary caller supplied field would expose the entity graph. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("occurredAt", "amount", "createdAt", "updatedAt", "type");

    private static final String DEFAULT_SORT_FIELD = "occurredAt";

    private final SpringDataTransactionRepository delegate;

    public TransactionRepositoryAdapter(SpringDataTransactionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MoneyTransaction save(MoneyTransaction transaction) {
        return delegate.save(transaction);
    }

    @Override
    public Optional<MoneyTransaction> findById(String id) {
        return delegate.findById(id).filter(transaction -> !transaction.isDeleted());
    }

    @Override
    public Optional<MoneyTransaction> findByIdIncludingDeleted(String id) {
        return delegate.findById(id);
    }

    @Override
    public Page<MoneyTransaction> search(TransactionFilter filter, PageRequest pageRequest) {
        org.springframework.data.domain.PageRequest springPage =
                org.springframework.data.domain.PageRequest.of(
                        pageRequest.page(), pageRequest.size(), sortOf(pageRequest));

        org.springframework.data.domain.Page<MoneyTransaction> result =
                delegate.findAll(TransactionSpecifications.from(filter), springPage);

        return new Page<>(
                result.getContent(), pageRequest.page(), pageRequest.size(), result.getTotalElements());
    }

    @Override
    public List<MoneyTransaction> findAll(TransactionFilter filter) {
        return delegate.findAll(
                TransactionSpecifications.from(filter), Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));
    }

    @Override
    public Movement movementFor(String walletId, Instant asOf) {
        List<Object[]> rows = delegate.sumMovementFor(walletId, asOf);
        return rows.isEmpty() ? Movement.NONE : movementOf(rows.get(0), 0);
    }

    @Override
    public Map<String, Movement> movementForAll(Instant asOf) {
        Map<String, Movement> movements = new HashMap<>();
        for (Object[] row : delegate.sumMovementForAll(asOf)) {
            movements.put((String) row[0], movementOf(row, 1));
        }
        return movements;
    }

    @Override
    public long sumExpense(List<String> categoryIds, Instant from, Instant to) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0L;
        }
        return delegate.sumExpense(categoryIds, from, to);
    }

    @Override
    public long countByWallet(String walletId) {
        return delegate.countByWallet(walletId);
    }

    @Override
    public long countByCategory(String categoryId) {
        return delegate.countByCategory(categoryId);
    }

    @Override
    public long sumByType(TransactionType type, Instant from, Instant to) {
        return delegate.sumByType(type, from, to);
    }

    /** Reads the received/spent pair out of an aggregate row starting at {@code offset}. */
    private Movement movementOf(Object[] row, int offset) {
        return new Movement(toLong(row[offset]), toLong(row[offset + 1]));
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Sort sortOf(PageRequest pageRequest) {
        String field = pageRequest.sortField();
        String resolved = field != null && SORTABLE_FIELDS.contains(field) ? field : DEFAULT_SORT_FIELD;
        Sort primary =
                Sort.by(pageRequest.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, resolved);

        // Tie breaker: several transactions can share the same occurredAt to the millisecond, and
        // paging over an unstable order repeats or skips rows between pages.
        return resolved.equals("createdAt")
                ? primary
                : primary.and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}

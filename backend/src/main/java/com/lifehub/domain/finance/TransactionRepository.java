package com.lifehub.domain.finance;

import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for transactions. */
public interface TransactionRepository {

    MoneyTransaction save(MoneyTransaction transaction);

    Optional<MoneyTransaction> findById(String id);

    Optional<MoneyTransaction> findByIdIncludingDeleted(String id);

    Page<MoneyTransaction> search(TransactionFilter filter, PageRequest pageRequest);

    /** Every live transaction matching a filter, for aggregation that must not be paged. */
    List<MoneyTransaction> findAll(TransactionFilter filter);

    /**
     * Money in and out of one wallet up to {@code asOf}, computed in SQL.
     *
     * <p>Summing in the database rather than loading rows keeps the balance query independent of
     * how many transactions exist, which is what makes NFR-PERF-03 reachable with 5.000 rows.
     *
     * @param asOf exclusive upper bound, or null for "everything so far"
     */
    Movement movementFor(String walletId, Instant asOf);

    /** The same aggregate for every wallet at once, so the wallet list costs one query. */
    Map<String, Movement> movementForAll(Instant asOf);

    /** Total expense in a category, and its children, over a half-open instant range. */
    long sumExpense(List<String> categoryIds, Instant from, Instant to);

    /** Live transaction count referencing a wallet as either source or destination. */
    long countByWallet(String walletId);

    long countByCategory(String categoryId);

    /** Sum of live transactions of one type over a half-open instant range. */
    long sumByType(TransactionType type, Instant from, Instant to);

    /**
     * Money entering and leaving one wallet.
     *
     * @param received incoming total as a positive magnitude
     * @param spent outgoing total as a positive magnitude
     */
    record Movement(long received, long spent) {
        public static final Movement NONE = new Movement(0L, 0L);
    }
}

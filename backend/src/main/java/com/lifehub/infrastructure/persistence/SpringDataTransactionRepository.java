package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data plumbing for the transaction table.
 *
 * <p>The aggregate queries here are the reason wallet balances can stay derived (FR-FIN-07): they
 * sum in SQL, so the cost of reading a balance does not grow with the number of transactions and
 * NFR-PERF-03 stays reachable at 5.000 rows.
 */
public interface SpringDataTransactionRepository
        extends JpaRepository<MoneyTransaction, String>, JpaSpecificationExecutor<MoneyTransaction> {

    /**
     * Money into and out of one wallet.
     *
     * <p>Returned as a single row of two sums rather than two queries, and expressed with CASE so
     * the four clauses of the balance formula in 03-DATA-MODEL.md 2.6 are visible in one place.
     */
    @Query("""
            SELECT
              COALESCE(SUM(CASE
                WHEN t.type = com.lifehub.domain.finance.TransactionType.INCOME AND t.wallet.id = :walletId THEN t.amount
                WHEN t.type = com.lifehub.domain.finance.TransactionType.TRANSFER AND t.toWallet.id = :walletId THEN t.amount
                ELSE 0 END), 0),
              COALESCE(SUM(CASE
                WHEN t.type = com.lifehub.domain.finance.TransactionType.EXPENSE AND t.wallet.id = :walletId THEN t.amount
                WHEN t.type = com.lifehub.domain.finance.TransactionType.TRANSFER AND t.wallet.id = :walletId THEN t.amount
                ELSE 0 END), 0)
            FROM MoneyTransaction t
            WHERE t.deletedAt IS NULL
              AND (t.wallet.id = :walletId OR t.toWallet.id = :walletId)
              AND (:asOf IS NULL OR t.occurredAt < :asOf)
            """)
    List<Object[]> sumMovementFor(@Param("walletId") String walletId, @Param("asOf") Instant asOf);
    /** The same aggregate for every wallet, one row per wallet that has any movement at all. */
    @Query("""
            SELECT w.id,
              COALESCE(SUM(CASE
                WHEN t.type = com.lifehub.domain.finance.TransactionType.INCOME AND t.wallet.id = w.id THEN t.amount
                WHEN t.type = com.lifehub.domain.finance.TransactionType.TRANSFER AND t.toWallet.id = w.id THEN t.amount
                ELSE 0 END), 0),
              COALESCE(SUM(CASE
                WHEN t.type = com.lifehub.domain.finance.TransactionType.EXPENSE AND t.wallet.id = w.id THEN t.amount
                WHEN t.type = com.lifehub.domain.finance.TransactionType.TRANSFER AND t.wallet.id = w.id THEN t.amount
                ELSE 0 END), 0)
            FROM Wallet w
            LEFT JOIN MoneyTransaction t
              ON (t.wallet.id = w.id OR t.toWallet.id = w.id)
              AND t.deletedAt IS NULL
              AND (:asOf IS NULL OR t.occurredAt < :asOf)
            WHERE w.deletedAt IS NULL
            GROUP BY w.id
            """)
    List<Object[]> sumMovementForAll(@Param("asOf") Instant asOf);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM MoneyTransaction t "
            + "WHERE t.deletedAt IS NULL AND t.type = com.lifehub.domain.finance.TransactionType.EXPENSE "
            + "AND t.category.id IN :categoryIds AND t.occurredAt >= :from AND t.occurredAt < :to")
    long sumExpense(
            @Param("categoryIds") List<String> categoryIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM MoneyTransaction t "
            + "WHERE t.deletedAt IS NULL AND t.type = :type "
            + "AND t.occurredAt >= :from AND t.occurredAt < :to")
    long sumByType(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT COUNT(t) FROM MoneyTransaction t WHERE t.deletedAt IS NULL "
            + "AND (t.wallet.id = :walletId OR t.toWallet.id = :walletId)")
    long countByWallet(@Param("walletId") String walletId);

    @Query("SELECT COUNT(t) FROM MoneyTransaction t WHERE t.deletedAt IS NULL AND t.category.id = :categoryId")
    long countByCategory(@Param("categoryId") String categoryId);
}

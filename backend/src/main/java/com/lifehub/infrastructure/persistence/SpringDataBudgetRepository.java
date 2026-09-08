package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Budget;
import com.lifehub.domain.finance.BudgetPeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data plumbing for the budget table.
 *
 * <p>Every query fetches the category <em>and</em> its parent. The response renders a
 * sub-category as "Ăn uống › Cà phê", so the parent name is part of what a budget row shows;
 * fetching it here keeps a list of budgets to one query instead of one per row.
 */
public interface SpringDataBudgetRepository extends JpaRepository<Budget, String> {

    @Query("SELECT b FROM Budget b JOIN FETCH b.category c LEFT JOIN FETCH c.parent "
            + "WHERE b.deletedAt IS NULL ORDER BY b.createdAt DESC")
    List<Budget> findAllLive();

    @Query("SELECT b FROM Budget b JOIN FETCH b.category c LEFT JOIN FETCH c.parent "
            + "WHERE b.deletedAt IS NULL AND b.active = true ORDER BY b.createdAt DESC")
    List<Budget> findAllActive();

    @Query("SELECT b FROM Budget b JOIN FETCH b.category c LEFT JOIN FETCH c.parent "
            + "WHERE b.deletedAt IS NULL AND b.active = true AND c.id IN :categoryIds")
    List<Budget> findActiveByCategoryIds(@Param("categoryIds") List<String> categoryIds);

    /** Single budget with the same graph the list queries load. */
    @Query("SELECT b FROM Budget b JOIN FETCH b.category c LEFT JOIN FETCH c.parent WHERE b.id = :id")
    Optional<Budget> findLiveById(@Param("id") String id);

    @Query("SELECT COUNT(b) > 0 FROM Budget b WHERE b.deletedAt IS NULL AND b.active = true "
            + "AND b.category.id = :categoryId AND b.period = :period "
            + "AND (:excludingId IS NULL OR b.id <> :excludingId)")
    boolean existsForCategoryAndPeriod(
            @Param("categoryId") String categoryId,
            @Param("period") BudgetPeriod period,
            @Param("excludingId") String excludingId);
}

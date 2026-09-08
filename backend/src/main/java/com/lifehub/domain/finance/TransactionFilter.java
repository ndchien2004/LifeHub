package com.lifehub.domain.finance;

import java.time.Instant;
import java.util.List;

/**
 * Every way the transaction list can be narrowed (FR-FIN-12).
 *
 * <p>All members are optional; a filter with nothing set matches every live transaction. Amount
 * bounds are plain longs rather than {@link Money} because "no lower bound" is a real state that a
 * value object with a zero has no way to express.
 *
 * @param from inclusive lower bound on {@code occurredAt}
 * @param to exclusive upper bound on {@code occurredAt}
 * @param categoryIds matched against the transaction category, expanded to include child
 *     categories by the query service before it reaches the repository
 */
public record TransactionFilter(
        Instant from,
        Instant to,
        List<String> walletIds,
        List<String> categoryIds,
        List<TransactionType> types,
        List<TransactionSource> sources,
        List<String> tagIds,
        Long minAmount,
        Long maxAmount,
        String keyword,
        boolean includeDeleted) {

    public static TransactionFilter empty() {
        return new TransactionFilter(
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, false);
    }

    /** A filter restricted to one date range, used by the summary and dashboard queries. */
    public static TransactionFilter between(Instant from, Instant to) {
        return new TransactionFilter(
                from, to, List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, false);
    }

    public TransactionFilter withCategoryIds(List<String> replacement) {
        return new TransactionFilter(
                from, to, walletIds, replacement, types, sources, tagIds, minAmount, maxAmount, keyword, includeDeleted);
    }

    public TransactionFilter withTypes(List<TransactionType> replacement) {
        return new TransactionFilter(
                from, to, walletIds, categoryIds, replacement, sources, tagIds, minAmount, maxAmount, keyword, includeDeleted);
    }

    public boolean hasTagFilter() {
        return tagIds != null && !tagIds.isEmpty();
    }
}

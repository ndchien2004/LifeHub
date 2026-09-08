package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.task.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates a {@link TransactionFilter} into a JPA Specification (FR-FIN-12).
 *
 * <p>Only criteria the caller actually set contribute a predicate, so an unfiltered list costs
 * nothing extra and combined criteria narrow with AND.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<MoneyTransaction> from(TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!filter.includeDeleted()) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), filter.to()));
            }
            if (isNotEmpty(filter.walletIds())) {
                // A transfer belongs to both of its wallets, so filtering by wallet has to look at
                // the destination too - otherwise money arriving in a wallet is invisible on it.
                predicates.add(cb.or(
                        root.get("wallet").get("id").in(filter.walletIds()),
                        root.get("toWallet").get("id").in(filter.walletIds())));
            }
            if (isNotEmpty(filter.categoryIds())) {
                predicates.add(root.get("category").get("id").in(filter.categoryIds()));
            }
            if (isNotEmpty(filter.types())) {
                predicates.add(root.get("type").in(filter.types()));
            }
            if (isNotEmpty(filter.sources())) {
                predicates.add(root.get("source").in(filter.sources()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("note"), "")), pattern));
            }
            if (filter.hasTagFilter()) {
                // A transaction carrying two requested tags would otherwise appear twice.
                if (query != null) {
                    query.distinct(true);
                }
                Join<MoneyTransaction, Tag> tags = root.join("tags", JoinType.INNER);
                predicates.add(tags.get("id").in(filter.tagIds()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isNotEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }
}

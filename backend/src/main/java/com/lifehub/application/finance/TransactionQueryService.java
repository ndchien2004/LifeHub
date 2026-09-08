package com.lifehub.application.finance;

import com.lifehub.application.finance.TransactionSummary.SummaryGroup;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.Page;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.SummaryGroupBy;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.Wallet;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the finance module (FR-FIN-10 → FR-FIN-12).
 *
 * <p>{@code open-in-view} is off, so every association the api layer will render has to be touched
 * before this class returns - see {@link #hydrate}.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String TRANSFER_LABEL = "Chuyển khoản";
    private static final String TRANSFER_COLOR = "#64748b";

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final ZoneId displayZone;

    public TransactionQueryService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            ZoneId displayZone) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.displayZone = displayZone;
    }

    public Page<MoneyTransaction> search(TransactionFilter filter, PageRequest pageRequest) {
        Page<MoneyTransaction> page = transactionRepository.search(expand(filter), pageRequest);
        hydrate(page.items());
        return page;
    }

    public MoneyTransaction findById(String id) {
        MoneyTransaction transaction = transactionRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
        hydrate(List.of(transaction));
        return transaction;
    }

    /**
     * Totals for a date range, grouped along one axis (06-API-SPEC.md 7).
     *
     * <p>Aggregated in memory rather than in SQL. Grouping by week or month means bucketing by
     * local calendar date, and SQLite stores instants as epoch milliseconds with no timezone-aware
     * date functions - doing it in SQL would mean reimplementing the calendar in string arithmetic
     * and getting the {@code Asia/Ho_Chi_Minh} offset wrong at the boundaries. The range is bounded
     * by the caller, so the row count stays proportional to the window being charted.
     */
    public TransactionSummary summarize(
            Instant from, Instant to, SummaryGroupBy groupBy, TransactionType type) {

        TransactionFilter filter = TransactionFilter.between(from, to);
        if (type != null) {
            filter = filter.withTypes(List.of(type));
        }

        List<MoneyTransaction> transactions = transactionRepository.findAll(expand(filter));
        hydrate(transactions);

        long totalIncome = sumOf(transactions, TransactionType.INCOME);
        long totalExpense = sumOf(transactions, TransactionType.EXPENSE);

        SummaryGroupBy axis = groupBy == null ? SummaryGroupBy.CATEGORY : groupBy;
        List<SummaryGroup> groups = group(transactions, axis, type);

        return TransactionSummary.of(totalIncome, totalExpense, groups);
    }

    /** Total of one transaction type over a half-open range, used by the dashboard (FR-SYS-01). */
    public long sumByType(TransactionType type, Instant from, Instant to) {
        return transactionRepository.sumByType(type, from, to);
    }

    /**
     * Widens a category filter to include child categories.
     *
     * <p>Filtering on "Ăn uống" has to return the coffees filed under "Ăn uống › Cà phê", otherwise
     * the parent looks empty to anyone who uses sub-categories at all.
     */
    private TransactionFilter expand(TransactionFilter filter) {
        if (filter.categoryIds() == null || filter.categoryIds().isEmpty()) {
            return filter;
        }
        List<String> expanded = new ArrayList<>(filter.categoryIds());
        for (String categoryId : filter.categoryIds()) {
            categoryRepository.findChildren(categoryId).forEach(child -> expanded.add(child.getId()));
        }
        return filter.withCategoryIds(expanded.stream().distinct().toList());
    }

    private long sumOf(List<MoneyTransaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .mapToLong(transaction -> transaction.getAmount().toLong())
                .sum();
    }

    private List<SummaryGroup> group(
            List<MoneyTransaction> transactions, SummaryGroupBy axis, TransactionType requestedType) {

        // With no explicit type the charts describe spending, which is what the pie is for.
        // Transfers never take part: they are internal movements, not money earned or spent.
        List<MoneyTransaction> relevant = transactions.stream()
                .filter(transaction -> requestedType != null
                        ? transaction.getType() == requestedType
                        : transaction.getType() != TransactionType.TRANSFER)
                .toList();

        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (MoneyTransaction transaction : relevant) {
            Bucket bucket = buckets.computeIfAbsent(keyOf(transaction, axis), key -> newBucket(transaction, axis, key));
            bucket.amount += transaction.getAmount().toLong();
            bucket.count++;
        }

        long total = buckets.values().stream().mapToLong(bucket -> bucket.amount).sum();

        List<SummaryGroup> groups = buckets.values().stream()
                .map(bucket -> new SummaryGroup(
                        bucket.key,
                        bucket.label,
                        bucket.color,
                        bucket.amount,
                        percentage(bucket.amount, total),
                        bucket.count))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Time axes read chronologically; category and wallet axes read largest first, which is
        // the order the pie chart legend needs.
        if (axis == SummaryGroupBy.DAY || axis == SummaryGroupBy.WEEK || axis == SummaryGroupBy.MONTH) {
            groups.sort(Comparator.comparing(SummaryGroup::key));
        } else {
            groups.sort(Comparator.comparingLong(SummaryGroup::amount).reversed());
        }
        return List.copyOf(groups);
    }

    private String keyOf(MoneyTransaction transaction, SummaryGroupBy axis) {
        LocalDate date = LocalDate.ofInstant(transaction.getOccurredAt(), displayZone);
        return switch (axis) {
            case CATEGORY -> transaction.getCategoryId() == null ? "TRANSFER" : transaction.getCategoryId();
            case WALLET -> transaction.getWalletId();
            case DAY -> date.format(DAY_KEY);
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(DAY_KEY);
            case MONTH -> date.withDayOfMonth(1).format(DAY_KEY);
        };
    }

    private Bucket newBucket(MoneyTransaction transaction, SummaryGroupBy axis, String key) {
        Bucket bucket = new Bucket();
        bucket.key = key;

        switch (axis) {
            case CATEGORY -> {
                Category category = transaction.getCategory();
                bucket.label = category == null ? TRANSFER_LABEL : category.getName();
                bucket.color = category == null ? TRANSFER_COLOR : category.getColor();
            }
            case WALLET -> {
                Wallet wallet = transaction.getWallet();
                bucket.label = wallet == null ? "—" : wallet.getName();
                bucket.color = TRANSFER_COLOR;
            }
            default -> {
                bucket.label = key;
                bucket.color = null;
            }
        }
        return bucket;
    }

    private double percentage(long amount, long total) {
        if (total == 0L) {
            return 0.0;
        }
        return Math.round((double) amount * 1000.0 / (double) total) / 10.0;
    }

    /**
     * Touches the lazy associations the response needs while the session is still open.
     *
     * <p>All three are batch loaded, so a page costs a handful of extra queries rather than one
     * per row.
     */
    private void hydrate(List<MoneyTransaction> transactions) {
        for (MoneyTransaction transaction : transactions) {
            if (transaction.getWallet() != null) {
                transaction.getWallet().getName();
            }
            if (transaction.getToWallet() != null) {
                transaction.getToWallet().getName();
            }
            Category category = transaction.getCategory();
            if (category != null) {
                category.getName();
                if (category.getParent() != null) {
                    category.getParent().getName();
                }
            }
            transaction.getTags().size();
        }
    }

    private static final class Bucket {
        private String key;
        private String label;
        private String color;
        private long amount;
        private long count;
    }
}

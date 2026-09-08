package com.lifehub.api.finance;

import com.lifehub.api.finance.FinanceDtos.BudgetAlertResponse;
import com.lifehub.api.finance.FinanceDtos.BudgetResponse;
import com.lifehub.api.finance.FinanceDtos.CategoryRef;
import com.lifehub.api.finance.FinanceDtos.CategoryResponse;
import com.lifehub.api.finance.FinanceDtos.CreateTransactionRequest;
import com.lifehub.api.finance.FinanceDtos.RecurringRuleResponse;
import com.lifehub.api.finance.FinanceDtos.SummaryGroupResponse;
import com.lifehub.api.finance.FinanceDtos.SummaryResponse;
import com.lifehub.api.finance.FinanceDtos.TagRef;
import com.lifehub.api.finance.FinanceDtos.TransactionResponse;
import com.lifehub.api.finance.FinanceDtos.TransactionWriteResponse;
import com.lifehub.api.finance.FinanceDtos.WalletBalanceResponse;
import com.lifehub.api.finance.FinanceDtos.WalletRef;
import com.lifehub.api.finance.FinanceDtos.WalletResponse;
import com.lifehub.application.finance.BudgetStatus;
import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.TransactionResult;
import com.lifehub.application.finance.TransactionSummary;
import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletBalance;
import com.lifehub.domain.system.RecurringRule;
import com.lifehub.domain.task.Tag;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts finance domain objects into API responses.
 *
 * <p>Instants become {@link OffsetDateTime} in the display zone, so the renderer receives
 * {@code 2026-09-04T12:15:00+07:00} rather than a bare UTC Z and never has to guess an offset.
 * Amounts are unwrapped from {@code Money} into plain integers of đồng at this boundary and nowhere
 * earlier.
 */
@Component
public class FinanceMapper {

    private final ZoneId displayZone;

    public FinanceMapper(ZoneId displayZone) {
        this.displayZone = displayZone;
    }

    public WalletResponse toResponse(Wallet wallet, WalletBalance balance) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getType(),
                wallet.getInitialBalance(),
                balance == null ? wallet.getInitialBalance() : balance.balance(),
                wallet.getCurrency(),
                wallet.getIcon(),
                wallet.isDefaultWallet(),
                wallet.getSortOrder(),
                toOffset(wallet.getCreatedAt()));
    }

    public WalletBalanceResponse toResponse(WalletBalance balance) {
        return new WalletBalanceResponse(
                balance.walletId(), balance.balance(), balance.asOfReceived(), balance.asOfSpent());
    }

    /**
     * Folds a flat category list into the two level tree the picker renders (FR-FIN-02).
     *
     * <p>Built here rather than in the service because it is purely a wire format concern: the
     * service works with the flat list, and the response shape is what the spec calls "dạng cây".
     */
    public List<CategoryResponse> toTree(List<Category> categories) {
        Map<String, List<Category>> childrenByParent = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();

        for (Category category : categories) {
            if (category.isRoot()) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                        .add(category);
            }
        }

        // A child whose parent was filtered out of the list would otherwise vanish entirely.
        for (Map.Entry<String, List<Category>> entry : childrenByParent.entrySet()) {
            boolean parentPresent = roots.stream().anyMatch(root -> root.getId().equals(entry.getKey()));
            if (!parentPresent) {
                roots.addAll(entry.getValue());
            }
        }

        return roots.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName))
                .map(root -> toResponse(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    public CategoryResponse toResponse(Category category, List<Category> children) {
        List<CategoryResponse> childResponses = children == null || children.isEmpty()
                ? null
                : children.stream()
                        .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName))
                        .map(child -> toResponse(child, List.of()))
                        .toList();

        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getColor(),
                category.isSystem(),
                category.getSortOrder(),
                childResponses);
    }

    public TransactionResponse toResponse(MoneyTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount().toLong(),
                toRef(transaction.getWallet()),
                toRef(transaction.getToWallet()),
                toRef(transaction.getCategory()),
                transaction.getNote(),
                toOffset(transaction.getOccurredAt()),
                transaction.getSource(),
                transaction.getAiConfidence(),
                transaction.getRecurringRuleId(),
                transaction.getTags().stream()
                        .sorted(Comparator.comparing(Tag::getName))
                        .map(this::toRef)
                        .toList(),
                toOffset(transaction.getCreatedAt()),
                toOffset(transaction.getUpdatedAt()),
                toOffset(transaction.getDeletedAt()));
    }

    public List<TransactionResponse> toResponses(List<MoneyTransaction> transactions) {
        return transactions.stream().map(this::toResponse).toList();
    }

    public TransactionWriteResponse toResponse(TransactionResult result) {
        return new TransactionWriteResponse(
                toResponse(result.transaction()),
                result.walletBalance(),
                result.toWalletBalance(),
                toResponse(result.budgetAlert()));
    }

    public BudgetAlertResponse toResponse(BudgetAlert alert) {
        return alert == null
                ? null
                : new BudgetAlertResponse(
                        alert.budgetId(),
                        alert.categoryId(),
                        alert.categoryName(),
                        round(alert.usage()),
                        alert.level(),
                        alert.limitAmount(),
                        alert.spentAmount());
    }

    public BudgetResponse toResponse(BudgetStatus status) {
        return new BudgetResponse(
                status.budget().getId(),
                toRef(status.budget().getCategory()),
                status.budget().getLimitAmount().toLong(),
                status.spent().toLong(),
                status.remaining().toLong(),
                round(status.usage()),
                status.level(),
                status.budget().getPeriod(),
                status.budget().getStartDate(),
                status.cycle().start(),
                status.cycle().endInclusive(),
                status.budget().isActive());
    }

    public SummaryResponse toResponse(TransactionSummary summary) {
        return new SummaryResponse(
                summary.totalIncome(),
                summary.totalExpense(),
                summary.net(),
                summary.groups().stream()
                        .map(group -> new SummaryGroupResponse(
                                group.key(),
                                group.label(),
                                group.color(),
                                group.amount(),
                                group.percentage(),
                                group.transactionCount()))
                        .toList());
    }

    public RecurringRuleResponse toResponse(RecurringRule rule, CreateTransaction template) {
        return new RecurringRuleResponse(
                rule.getId(),
                rule.getRrule(),
                rule.getNextRunDate(),
                rule.getLastRunDate(),
                rule.isActive(),
                template == null ? null : toRequest(template));
    }

    /** Renders a stored template back as the request shape the edit form posted. */
    public CreateTransactionRequest toRequest(CreateTransaction template) {
        return new CreateTransactionRequest(
                template.type(),
                template.amount(),
                template.walletId(),
                template.toWalletId(),
                template.categoryId(),
                template.note(),
                toOffset(template.occurredAt()),
                template.tagIds(),
                template.source(),
                template.aiConfidence());
    }

    public WalletRef toRef(Wallet wallet) {
        return wallet == null ? null : new WalletRef(wallet.getId(), wallet.getName(), wallet.getIcon());
    }

    public CategoryRef toRef(Category category) {
        if (category == null) {
            return null;
        }
        Category parent = category.getParent();
        return new CategoryRef(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                parent == null ? null : parent.getName());
    }

    public TagRef toRef(Tag tag) {
        return new TagRef(tag.getId(), tag.getName(), tag.getColor());
    }

    public OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(displayZone).toOffsetDateTime();
    }

    public Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** Usage as a ratio with four decimals - enough for a percentage, short enough to read. */
    private double round(double usage) {
        return Math.round(usage * 10_000.0) / 10_000.0;
    }
}

package com.lifehub.application.finance;

import com.lifehub.domain.finance.BudgetPeriod;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionSource;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.WalletType;
import com.lifehub.domain.common.Patch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Input objects for the finance write services.
 *
 * <p>Create commands take plain values; update commands wrap every member in {@link Patch} so a
 * field the caller omitted stays untouched while one sent explicitly as null is cleared.
 */
public final class FinanceCommands {

    private FinanceCommands() {
    }

    public record CreateWallet(
            String name, WalletType type, Long initialBalance, String currency, String icon,
            Boolean isDefault, Integer sortOrder) {
    }

    public record UpdateWallet(
            Patch<String> name,
            Patch<WalletType> type,
            Patch<Long> initialBalance,
            Patch<String> currency,
            Patch<String> icon,
            Patch<Boolean> isDefault,
            Patch<Integer> sortOrder) {
    }

    public record CreateCategory(
            String name, CategoryType type, String parentId, String icon, String color, Integer sortOrder) {
    }

    public record UpdateCategory(
            Patch<String> name,
            Patch<String> parentId,
            Patch<String> icon,
            Patch<String> color,
            Patch<Integer> sortOrder) {
    }

    public record CreateTransaction(
            TransactionType type,
            long amount,
            String walletId,
            String toWalletId,
            String categoryId,
            String note,
            Instant occurredAt,
            List<String> tagIds,
            TransactionSource source,
            Double aiConfidence,
            String recurringRuleId,
            String importHash) {
    }

    public record UpdateTransaction(
            Patch<TransactionType> type,
            Patch<Long> amount,
            Patch<String> walletId,
            Patch<String> toWalletId,
            Patch<String> categoryId,
            Patch<String> note,
            Patch<Instant> occurredAt,
            Patch<List<String>> tagIds) {
    }

    public record CreateBudget(
            String categoryId, long limitAmount, BudgetPeriod period, LocalDate startDate, Boolean isActive) {
    }

    public record UpdateBudget(
            Patch<String> categoryId,
            Patch<Long> limitAmount,
            Patch<BudgetPeriod> period,
            Patch<LocalDate> startDate,
            Patch<Boolean> isActive) {
    }

    /** A recurring rule plus the transaction template it produces (FR-FIN-13). */
    public record CreateRecurringRule(String rrule, LocalDate startDate, CreateTransaction template) {
    }

    public record UpdateRecurringRule(
            Patch<String> rrule, Patch<Boolean> isActive, Patch<LocalDate> nextRunDate) {
    }
}

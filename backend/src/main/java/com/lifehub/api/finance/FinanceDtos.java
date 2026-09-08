package com.lifehub.api.finance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifehub.domain.finance.BudgetAlertLevel;
import com.lifehub.domain.finance.BudgetPeriod;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionSource;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.WalletType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request and response shapes for the finance module (06-API-SPEC.md 7).
 *
 * <p>Every amount crosses the wire as a plain JSON integer of đồng, never a decimal - matching the
 * "Số tiền: số nguyên, đơn vị đồng" convention in 06-API-SPEC.md 1 and keeping JavaScript's binary
 * floating point out of the ledger.
 *
 * <p>Timestamps cross as {@link OffsetDateTime} in the machine local zone; dates that carry no time
 * of their own, such as a budget start, cross as bare {@code yyyy-MM-dd}.
 */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---------- responses ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WalletResponse(
            String id,
            String name,
            WalletType type,
            long initialBalance,
            long balance,
            String currency,
            String icon,
            boolean isDefault,
            int sortOrder,
            OffsetDateTime createdAt) {
    }

    /** The wallet list plus the total the screen shows above it (FR-FIN-01). */
    public record WalletListResponse(List<WalletResponse> wallets, long totalAssets) {
    }

    public record WalletBalanceResponse(String walletId, long balance, long received, long spent) {
    }

    /** A category node; {@code children} is null for a leaf so the tree stays compact. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoryResponse(
            String id,
            String parentId,
            String name,
            CategoryType type,
            String icon,
            String color,
            boolean isSystem,
            int sortOrder,
            List<CategoryResponse> children) {
    }

    public record WalletRef(String id, String name, String icon) {
    }

    public record CategoryRef(String id, String name, String icon, String color, String parentName) {
    }

    public record TagRef(String id, String name, String color) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionResponse(
            String id,
            TransactionType type,
            long amount,
            WalletRef wallet,
            WalletRef toWallet,
            CategoryRef category,
            String note,
            OffsetDateTime occurredAt,
            TransactionSource source,
            Double aiConfidence,
            String recurringRuleId,
            List<TagRef> tags,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt) {
    }

    /** What a write returns: the row, the balances it moved, and any budget it pushed over. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionWriteResponse(
            TransactionResponse transaction,
            long walletBalance,
            Long toWalletBalance,
            BudgetAlertResponse budgetAlert) {
    }

    public record BudgetAlertResponse(
            String budgetId,
            String categoryId,
            String categoryName,
            double usage,
            BudgetAlertLevel level,
            long limitAmount,
            long spentAmount) {
    }

    public record SummaryGroupResponse(
            String key,
            String label,
            String color,
            long amount,
            double percentage,
            long transactionCount) {
    }

    public record SummaryResponse(
            long totalIncome,
            long totalExpense,
            long net,
            List<SummaryGroupResponse> groups) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BudgetResponse(
            String id,
            CategoryRef category,
            long limitAmount,
            long spentAmount,
            long remainingAmount,
            double usage,
            BudgetAlertLevel level,
            BudgetPeriod period,
            LocalDate startDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean isActive) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecurringRuleResponse(
            String id,
            String rrule,
            LocalDate nextRunDate,
            LocalDate lastRunDate,
            boolean isActive,
            CreateTransactionRequest template) {
    }

    // ---------- requests ----------

    public record CreateWalletRequest(
            @NotBlank(message = "Tên ví không được để trống")
            @Size(max = 100, message = "Tên ví tối đa 100 ký tự")
            String name,
            WalletType type,
            Long initialBalance,
            String currency,
            String icon,
            Boolean isDefault,
            Integer sortOrder) {
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "Tên danh mục không được để trống")
            @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
            String name,
            CategoryType type,
            String parentId,
            String icon,
            String color,
            Integer sortOrder) {
    }

    public record CreateTransactionRequest(
            @NotNull(message = "Loại giao dịch không được để trống") TransactionType type,
            @Positive(message = "Số tiền phải lớn hơn 0") long amount,
            @NotBlank(message = "Ví không được để trống") String walletId,
            String toWalletId,
            String categoryId,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
            OffsetDateTime occurredAt,
            List<String> tagIds,
            TransactionSource source,
            Double aiConfidence) {
    }

    public record CreateBudgetRequest(
            @NotBlank(message = "Danh mục không được để trống") String categoryId,
            @Positive(message = "Hạn mức phải lớn hơn 0") long limitAmount,
            BudgetPeriod period,
            LocalDate startDate,
            Boolean isActive) {
    }

    public record CreateRecurringRuleRequest(
            @NotBlank(message = "Quy luật lặp không được để trống") String rrule,
            LocalDate startDate,
            @NotNull(message = "Cần mẫu giao dịch") CreateTransactionRequest template) {
    }
}

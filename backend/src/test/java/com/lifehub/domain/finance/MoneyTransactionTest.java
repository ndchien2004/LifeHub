package com.lifehub.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.common.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T3-05, T3-06, T3-07 — the shape rules a transaction must satisfy. */
class MoneyTransactionTest {

    private static final Instant NOW = Instant.parse("2026-09-08T05:00:00Z");

    private final Wallet cash = wallet("Tiền mặt");
    private final Wallet bank = wallet("Vietcombank");
    private final Category food = new Category("Ăn uống", CategoryType.EXPENSE, null, null, "#f59e0b");
    private final Category salary = new Category("Lương", CategoryType.INCOME, null, null, "#22c55e");

    @Test
    @DisplayName("T3-05 — chuyển khoản làm ví nguồn giảm và ví đích tăng cùng số tiền")
    void transferMovesTheSameAmountBetweenTwoWallets() {
        MoneyTransaction transfer = new MoneyTransaction(
                TransactionType.TRANSFER, Money.of(500_000L), cash, bank, null, NOW);

        assertThat(transfer.effectOn(cash.getId())).isEqualTo(-500_000L);
        assertThat(transfer.effectOn(bank.getId())).isEqualTo(500_000L);
        assertThat(transfer.effectOn("some-other-wallet")).isZero();
        assertThat(transfer.getCategory()).as("chuyển khoản không cần danh mục").isNull();
    }

    @Test
    @DisplayName("T3-06 — chuyển khoản cùng một ví bị chặn")
    void rejectsATransferToTheSameWallet() {
        assertThatThrownBy(() -> new MoneyTransaction(
                        TransactionType.TRANSFER, Money.of(100_000L), cash, cash, null, NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Ví nguồn và ví đích phải khác nhau");
    }

    @Test
    @DisplayName("Chuyển khoản thiếu ví đích bị chặn")
    void rejectsATransferWithNoDestination() {
        assertThatThrownBy(() -> new MoneyTransaction(
                        TransactionType.TRANSFER, Money.of(100_000L), cash, null, null, NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ví đích");
    }

    @Test
    @DisplayName("T3-07 — giao dịch INCOME gắn danh mục EXPENSE bị chặn")
    void rejectsAMismatchedCategory() {
        assertThatThrownBy(() -> new MoneyTransaction(
                        TransactionType.INCOME, Money.of(15_000_000L), bank, null, food, NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Danh mục không khớp");
    }

    @Test
    @DisplayName("Giao dịch thu/chi thiếu danh mục bị chặn")
    void rejectsANonTransferWithoutACategory() {
        assertThatThrownBy(() -> new MoneyTransaction(
                        TransactionType.EXPENSE, Money.of(45_000L), cash, null, null, NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cần danh mục");
    }

    @Test
    @DisplayName("Số tiền phải lớn hơn 0")
    void rejectsAZeroAmount() {
        assertThatThrownBy(() -> new MoneyTransaction(
                        TransactionType.EXPENSE, Money.ZERO, cash, null, food, NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("lớn hơn 0");
    }

    @Test
    @DisplayName("Thu làm ví tăng, chi làm ví giảm")
    void incomeAddsAndExpenseSubtracts() {
        MoneyTransaction income = new MoneyTransaction(
                TransactionType.INCOME, Money.of(15_000_000L), bank, null, salary, NOW);
        MoneyTransaction expense = new MoneyTransaction(
                TransactionType.EXPENSE, Money.of(45_000L), cash, null, food, NOW);

        assertThat(income.effectOn(bank.getId())).isEqualTo(15_000_000L);
        assertThat(income.effectOn(cash.getId())).isZero();
        assertThat(expense.effectOn(cash.getId())).isEqualTo(-45_000L);
    }

    /**
     * Switching direction has to re-validate, not merely relabel: an expense turned into a transfer
     * must acquire a destination wallet and lose its category in the same step.
     */
    @Test
    @DisplayName("Đổi loại giao dịch kéo theo kiểm tra lại ví và danh mục")
    void changingTypeRevalidatesTheWholeShape() {
        MoneyTransaction transaction = new MoneyTransaction(
                TransactionType.EXPENSE, Money.of(200_000L), cash, null, food, NOW);

        transaction.changeType(TransactionType.TRANSFER, bank, null);

        assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.getToWalletId()).isEqualTo(bank.getId());

        assertThatThrownBy(() -> transaction.changeType(TransactionType.INCOME, null, food))
                .as("thu không thể mang danh mục chi")
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Độ tin cậy AI bị bỏ đi khi giao dịch không còn do AI tạo")
    void dropsAiConfidenceForNonAiSources() {
        MoneyTransaction transaction = new MoneyTransaction(
                TransactionType.EXPENSE, Money.of(45_000L), cash, null, food, NOW);

        transaction.changeSource(TransactionSource.AI_PARSE, 0.87);
        assertThat(transaction.getAiConfidence()).isEqualTo(0.87);

        transaction.changeSource(TransactionSource.MANUAL, 0.87);
        assertThat(transaction.getAiConfidence()).isNull();
    }

    private Wallet wallet(String name) {
        return new Wallet(name, WalletType.CASH, 0L, "VND", null);
    }
}

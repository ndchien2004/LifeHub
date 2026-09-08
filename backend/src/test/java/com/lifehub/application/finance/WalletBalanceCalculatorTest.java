package com.lifehub.application.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lifehub.domain.finance.Money;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.TransactionRepository.Movement;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletBalance;
import com.lifehub.domain.finance.WalletRepository;
import com.lifehub.domain.finance.WalletType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T3-03, T3-04, T3-05 — the derived balance formula of 03-DATA-MODEL.md 2.6. */
class WalletBalanceCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-09-08T05:00:00Z");

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final WalletBalanceCalculator calculator =
            new WalletBalanceCalculator(walletRepository, transactionRepository);

    @Test
    @DisplayName("T3-03 — ví chỉ có giao dịch thu: số dư = số dư ban đầu + tổng thu")
    void addsIncomeToTheOpeningBalance() {
        Wallet wallet = wallet("Vietcombank", 1_000_000L);
        when(transactionRepository.movementFor(eq(wallet.getId()), any()))
                .thenReturn(new Movement(15_000_000L, 0L));

        WalletBalance balance = calculator.balanceOf(wallet);

        assertThat(balance.balance()).isEqualTo(16_000_000L);
        assertThat(balance.asOfReceived()).isEqualTo(15_000_000L);
        assertThat(balance.asOfSpent()).isZero();
    }

    @Test
    @DisplayName("T3-04 — ví hỗn hợp mọi loại giao dịch tính đúng công thức đầy đủ")
    void appliesEveryClauseOfTheFormula() {
        Wallet cash = wallet("Tiền mặt", 2_000_000L);
        Wallet bank = wallet("Vietcombank", 5_000_000L);

        // Cash: +3.000.000 lương, -450.000 ăn uống, -1.000.000 chuyển sang bank.
        // Bank: +1.000.000 nhận chuyển khoản, -2.000.000 tiền nhà.
        List<MoneyTransaction> ledger = List.of(
                transaction(TransactionType.INCOME, 3_000_000L, cash, null),
                transaction(TransactionType.EXPENSE, 450_000L, cash, null),
                transaction(TransactionType.TRANSFER, 1_000_000L, cash, bank),
                transaction(TransactionType.EXPENSE, 2_000_000L, bank, null));

        when(transactionRepository.movementForAll(null))
                .thenReturn(Map.of(
                        cash.getId(), movementOf(ledger, cash.getId()),
                        bank.getId(), movementOf(ledger, bank.getId())));

        Map<String, WalletBalance> balances = calculator.balancesOf(List.of(cash, bank));

        assertThat(balances.get(cash.getId()).balance()).isEqualTo(3_550_000L);
        assertThat(balances.get(bank.getId()).balance()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("T3-05 — chuyển khoản trừ ví nguồn và cộng ví đích đúng bằng nhau")
    void aTransferIsExactlySymmetric() {
        Wallet source = wallet("Tiền mặt", 0L);
        Wallet destination = wallet("Momo", 0L);
        MoneyTransaction transfer = transaction(TransactionType.TRANSFER, 750_000L, source, destination);

        long fromSource = transfer.effectOn(source.getId());
        long toDestination = transfer.effectOn(destination.getId());

        assertThat(fromSource).isEqualTo(-750_000L);
        assertThat(toDestination).isEqualTo(750_000L);
        assertThat(fromSource + toDestination)
                .as("chuyển khoản không tạo ra và không làm mất tiền trong tổng tài sản")
                .isZero();
    }

    /**
     * The exactness guarantee of FR-FIN-06, at the arithmetic level.
     *
     * <p>A thousand pseudo-random amounts are summed two independent ways - by walking the ledger
     * transaction by transaction, and through the balance formula - and the two have to agree to
     * the đồng. The seed is fixed so a failure is reproducible.
     */
    @Test
    @DisplayName("Cộng 1.000 giao dịch ngẫu nhiên: số dư khớp tuyệt đối, không sai một đồng")
    void staysExactAcrossAThousandTransactions() {
        Wallet cash = wallet("Tiền mặt", 1_234_567L);
        Wallet bank = wallet("Vietcombank", 7_654_321L);

        Random random = new Random(20260908L);
        long expectedCash = cash.getInitialBalance();
        long expectedBank = bank.getInitialBalance();
        long netIncome = 0L;

        for (int i = 0; i < 1_000; i++) {
            long amount = 1L + random.nextInt(9_999_999);
            TransactionType type = TransactionType.values()[random.nextInt(3)];
            MoneyTransaction transaction = type == TransactionType.TRANSFER
                    ? transaction(type, amount, cash, bank)
                    : transaction(type, amount, cash, null);

            expectedCash += transaction.effectOn(cash.getId());
            expectedBank += transaction.effectOn(bank.getId());
            netIncome += transaction.effectOn(cash.getId()) + transaction.effectOn(bank.getId());
        }

        when(transactionRepository.movementForAll(null))
                .thenReturn(Map.of(
                        cash.getId(), new Movement(Math.max(expectedCash - cash.getInitialBalance(), 0L),
                                Math.max(cash.getInitialBalance() - expectedCash, 0L)),
                        bank.getId(), new Movement(Math.max(expectedBank - bank.getInitialBalance(), 0L),
                                Math.max(bank.getInitialBalance() - expectedBank, 0L))));

        Map<String, WalletBalance> balances = calculator.balancesOf(List.of(cash, bank));

        assertThat(balances.get(cash.getId()).balance()).isEqualTo(expectedCash);
        assertThat(balances.get(bank.getId()).balance()).isEqualTo(expectedBank);
        assertThat(expectedCash + expectedBank)
                .as("tổng tài sản chỉ đổi theo thu và chi — mọi chuyển khoản triệt tiêu")
                .isEqualTo(cash.getInitialBalance() + bank.getInitialBalance() + netIncome);
    }

    private Movement movementOf(List<MoneyTransaction> ledger, String walletId) {
        long received = 0L;
        long spent = 0L;
        for (MoneyTransaction transaction : ledger) {
            long effect = transaction.effectOn(walletId);
            if (effect > 0) {
                received += effect;
            } else {
                spent += -effect;
            }
        }
        return new Movement(received, spent);
    }

    private Wallet wallet(String name, long initialBalance) {
        return new Wallet(name, WalletType.CASH, initialBalance, "VND", null);
    }

    private MoneyTransaction transaction(TransactionType type, long amount, Wallet from, Wallet to) {
        Category category = type == TransactionType.TRANSFER
                ? null
                : new Category(
                        type == TransactionType.INCOME ? "Lương" : "Ăn uống",
                        type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE,
                        null,
                        null,
                        "#94a3b8");
        return new MoneyTransaction(type, Money.of(amount), from, to, category, NOW);
    }
}

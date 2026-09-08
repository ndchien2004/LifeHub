package com.lifehub.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.Money;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletRepository;
import com.lifehub.domain.finance.WalletType;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T3-14 — five thousand transactions, first page under 500 ms (NFR-PERF-03).
 *
 * <p>As with the task list, the risk is not raw speed but an N+1: every row carries a wallet, a
 * category with its parent and a tag set. The wallet list is measured too, because its balances are
 * derived rather than stored, and the only way that stays affordable is by aggregating in SQL.
 */
class FinancePerformanceIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final int TRANSACTION_COUNT = 5_000;
    private static final long BUDGET_MS = 500;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private static boolean seeded;
    private static long expectedCashBalance;

    @BeforeAll
    static void resetSeedFlag() {
        seeded = false;
    }

    private void seedOnce() {
        if (seeded) {
            return;
        }
        Wallet cash = walletRepository.save(new Wallet("Tiền mặt", WalletType.CASH, 50_000_000L, "VND", null));
        Wallet bank = walletRepository.save(new Wallet("Vietcombank", WalletType.BANK, 120_000_000L, "VND", null));

        List<Category> expenseCategories = categoryRepository.findAll(CategoryType.EXPENSE);
        List<Category> incomeCategories = categoryRepository.findAll(CategoryType.INCOME);

        Instant now = Instant.now();
        Random random = new Random(2026);
        long balance = cash.getInitialBalance();

        for (int i = 0; i < TRANSACTION_COUNT; i++) {
            long amount = 1_000L + random.nextInt(2_000_000);
            Instant occurredAt = now.minus(random.nextInt(365), ChronoUnit.DAYS);
            int kind = random.nextInt(10);

            MoneyTransaction transaction;
            if (kind < 6) {
                transaction = new MoneyTransaction(
                        TransactionType.EXPENSE,
                        Money.of(amount),
                        cash,
                        null,
                        expenseCategories.get(random.nextInt(expenseCategories.size())),
                        occurredAt);
            } else if (kind < 9) {
                transaction = new MoneyTransaction(
                        TransactionType.INCOME,
                        Money.of(amount),
                        cash,
                        null,
                        incomeCategories.get(random.nextInt(incomeCategories.size())),
                        occurredAt);
            } else {
                transaction = new MoneyTransaction(
                        TransactionType.TRANSFER, Money.of(amount), cash, bank, null, occurredAt);
            }

            transaction.annotate("Giao dịch số " + i);
            transactionRepository.save(transaction);
            balance += transaction.effectOn(cash.getId());
        }

        expectedCashBalance = balance;
        seeded = true;
    }

    @Test
    @DisplayName("T3-14 — 5.000 giao dịch, load trang đầu 50 dòng trong ≤ 500 ms")
    void loadsTheFirstPageWithinBudget() throws Exception {
        seedOnce();

        // Warm up: the first call pays Hibernate query plan compilation and JIT, a one-off startup
        // cost rather than the per-request cost NFR-PERF-03 is about.
        mockMvc.perform(authed(get("/api/v1/transactions").param("size", "50"))).andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(authed(get("/api/v1/transactions").param("size", "50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(50))
                .andExpect(jsonPath("$.data.totalItems").value(TRANSACTION_COUNT));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
                .as("T3-14 / NFR-PERF-03: trang đầu của 5.000 giao dịch phải trả trong %d ms, thực tế %d ms",
                        BUDGET_MS, elapsedMs)
                .isLessThanOrEqualTo(BUDGET_MS);
    }

    @Test
    @DisplayName("Số dư ví vẫn chính xác tuyệt đối trên 5.000 giao dịch và tính trong ≤ 500 ms")
    void derivesBalancesAccuratelyAndQuickly() throws Exception {
        seedOnce();

        mockMvc.perform(authed(get("/api/v1/wallets"))).andExpect(status().isOk());

        long start = System.nanoTime();
        String body = body(mockMvc.perform(authed(get("/api/v1/wallets")))
                .andExpect(status().isOk())
                .andReturn());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        long actual = objectMapper
                .readTree(body)
                .path("data")
                .path("wallets")
                .get(0)
                .path("balance")
                .asLong();

        assertThat(actual)
                .as("số dư tính động phải khớp tuyệt đối với tổng đã cộng độc lập trong test")
                .isEqualTo(expectedCashBalance);
        assertThat(elapsedMs)
                .as("số dư ví tính bằng SUM trong SQL nên không phụ thuộc số lượng giao dịch")
                .isLessThanOrEqualTo(BUDGET_MS);
    }

    @Test
    @DisplayName("Tổng hợp theo danh mục trên 5.000 giao dịch vẫn nằm trong ngân sách thời gian")
    void summarisesWithinBudget() throws Exception {
        seedOnce();

        mockMvc.perform(authed(get("/api/v1/transactions/summary").param("groupBy", "CATEGORY")))
                .andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(authed(get("/api/v1/transactions/summary")
                        .param("groupBy", "CATEGORY")
                        .param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isArray());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThanOrEqualTo(BUDGET_MS);
    }
}

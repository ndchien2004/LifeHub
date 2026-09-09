package com.lifehub.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.CreateWallet;
import com.lifehub.application.finance.TransactionService;
import com.lifehub.application.finance.WalletService;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.CategoryOption;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.WalletType;
import com.lifehub.support.TestDatabase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * UC-09 step 6 — the context the parsers and the prompt are built from.
 *
 * <p>Runs against a real database rather than mocks, because the thing most likely to break here is
 * a lazy association read after its session closed - which is exactly what mocks hide.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiContextProviderIT {

    private static final String DATABASE_URL = TestDatabase.freshUrl();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    /** The seeded sub-category "Cà phê" under "Ăn uống" (V5). */
    private static final String COFFEE_CATEGORY_ID = "01900000-0000-7000-8000-00000000e103";

    @Autowired
    private AiContextProvider provider;

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Nạp được danh mục hai cấp kèm tên danh mục cha, không nổ lazy loading")
    void loadsTheTwoLevelCategoryTree() {
        ParseContext context = provider.load();

        CategoryOption coffee = context.categories().stream()
                .filter(option -> option.id().equals(COFFEE_CATEGORY_ID))
                .findFirst()
                .orElseThrow();

        assertThat(coffee.name()).isEqualTo("Cà phê");
        assertThat(coffee.parentName())
                .as("Tên danh mục cha phải được đọc khi session còn mở")
                .isEqualTo("Ăn uống");
        assertThat(coffee.label()).isEqualTo("Ăn uống › Cà phê");
        assertThat(coffee.type()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    @DisplayName("Ví mặc định và mốc thời gian hiện tại đi kèm ngữ cảnh")
    void loadsWalletsAndTheClock() {
        String walletId = walletService
                .create(new CreateWallet("Tiền mặt", WalletType.CASH, 0L, "VND", null, true, 0))
                .getId();

        ParseContext context = provider.load();

        assertThat(context.wallets()).extracting(ParseContext.WalletOption::id).contains(walletId);
        assertThat(context.defaultWallet()).isPresent();
        assertThat(context.defaultWallet().orElseThrow().name()).isEqualTo("Tiền mặt");
        assertThat(context.now()).isBeforeOrEqualTo(Instant.now());
        assertThat(context.zone().getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("Chỉ lấy tối đa 30 giao dịch gần nhất, và chỉ những giao dịch có ghi chú")
    void capsTheRecentTransactionSample() {
        String walletId = walletService
                .create(new CreateWallet("Ví mẫu", WalletType.CASH, 0L, "VND", null, false, 0))
                .getId();

        for (int i = 0; i < 35; i++) {
            transactionService.create(new CreateTransaction(
                    TransactionType.EXPENSE,
                    45_000L,
                    walletId,
                    null,
                    COFFEE_CATEGORY_ID,
                    "Cà phê lần " + i,
                    Instant.now().minusSeconds(60L * i),
                    List.of(),
                    null,
                    null,
                    null,
                    null));
        }
        // One without a note: it teaches the model nothing, so it must not take up a slot.
        transactionService.create(new CreateTransaction(
                TransactionType.EXPENSE, 10_000L, walletId, null, COFFEE_CATEGORY_ID, null,
                Instant.now(), List.of(), null, null, null, null));

        ParseContext context = provider.load();

        assertThat(context.recentTransactions())
                .as("AGENTS.md 3.4 quy tắc 6: tối đa 30 giao dịch gần nhất")
                .hasSizeLessThanOrEqualTo(ParseContext.MAX_RECENT_TRANSACTIONS);
        assertThat(context.recentTransactions())
                .allSatisfy(row -> assertThat(row.note()).isNotBlank());
        assertThat(context.recentTransactions())
                .first()
                .satisfies(row -> assertThat(row.categoryName()).isEqualTo("Ăn uống › Cà phê"));
    }

    @Test
    @DisplayName("emptyContext chỉ mang đồng hồ và múi giờ")
    void emptyContextCarriesNoUserData() {
        ParseContext context = provider.emptyContext();

        assertThat(context.categories()).isEmpty();
        assertThat(context.wallets()).isEmpty();
        assertThat(context.recentTransactions()).isEmpty();
        assertThat(context.zone().getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }
}

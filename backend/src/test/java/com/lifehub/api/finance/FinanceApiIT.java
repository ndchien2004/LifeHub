package com.lifehub.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.support.ApiIntegrationTest;
import com.lifehub.support.TestDatabase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Finance module over HTTP: SD-01 end to end, plus T3-11 → T3-15. */
class FinanceApiIT extends ApiIntegrationTest {

    private static final String DATABASE_URL = TestDatabase.freshUrl();
    private static final String FOOD_CATEGORY = "01900000-0000-7000-8000-00000000e001";
    private static final String COFFEE_CATEGORY = "01900000-0000-7000-8000-00000000e103";
    private static final String SALARY_CATEGORY = "01900000-0000-7000-8000-00000000a001";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Test
    @DisplayName("SD-01 — tạo giao dịch chi làm số dư ví giảm đúng số tiền")
    void expenseReducesTheWalletBalance() throws Exception {
        String wallet = createWallet("Ví chi tiêu", 1_000_000L);

        JsonNode result = data(mockMvc.perform(authed(post("/api/v1/transactions"))
                        .content(json(expense(wallet, 45_000L, "Cơm gà"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.amount").value(45_000))
                .andReturn());

        assertThat(result.path("walletBalance").asLong()).isEqualTo(955_000L);
        assertThat(balanceOf(wallet)).isEqualTo(955_000L);
    }

    @Test
    @DisplayName("Chuyển khoản làm ví nguồn giảm và ví đích tăng cùng số tiền")
    void transferMovesMoneyBetweenTwoWallets() throws Exception {
        String source = createWallet("Ví nguồn", 2_000_000L);
        String destination = createWallet("Ví đích", 0L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "TRANSFER");
        body.put("amount", 500_000L);
        body.put("walletId", source);
        body.put("toWalletId", destination);
        body.put("occurredAt", now());

        JsonNode result = data(mockMvc.perform(authed(post("/api/v1/transactions")).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(result.path("walletBalance").asLong()).isEqualTo(1_500_000L);
        assertThat(result.path("toWalletBalance").asLong()).isEqualTo(500_000L);
        assertThat(balanceOf(destination)).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("Không tạo được chuyển khoản với cùng một ví")
    void refusesATransferToTheSameWallet() throws Exception {
        String wallet = createWallet("Ví một mình", 1_000_000L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "TRANSFER");
        body.put("amount", 100_000L);
        body.put("walletId", wallet);
        body.put("toWalletId", wallet);
        body.put("occurredAt", now());

        mockMvc.perform(authed(post("/api/v1/transactions")).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(balanceOf(wallet)).as("số dư không đổi khi giao dịch bị từ chối").isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("T3-13 — sửa số tiền giao dịch thì số dư được tính lại đúng")
    void recalculatesTheBalanceAfterAnEdit() throws Exception {
        String wallet = createWallet("Ví sửa", 1_000_000L);
        String id = createTransaction(expense(wallet, 100_000L, "Ăn trưa"));
        assertThat(balanceOf(wallet)).isEqualTo(900_000L);

        JsonNode result = data(mockMvc.perform(authed(patch("/api/v1/transactions/" + id))
                        .content(json(Map.of("amount", 250_000))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(result.path("walletBalance").asLong()).isEqualTo(750_000L);
        assertThat(balanceOf(wallet)).isEqualTo(750_000L);
    }

    @Test
    @DisplayName("Xóa giao dịch khôi phục số dư, khôi phục giao dịch thì trừ lại")
    void deletingAndRestoringRoundTripTheBalance() throws Exception {
        String wallet = createWallet("Ví xóa", 1_000_000L);
        String id = createTransaction(expense(wallet, 300_000L, "Mua sách"));
        assertThat(balanceOf(wallet)).isEqualTo(700_000L);

        mockMvc.perform(authed(delete("/api/v1/transactions/" + id))).andExpect(status().isOk());
        assertThat(balanceOf(wallet)).isEqualTo(1_000_000L);

        mockMvc.perform(authed(post("/api/v1/transactions/" + id + "/restore")))
                .andExpect(status().isOk());
        assertThat(balanceOf(wallet)).isEqualTo(700_000L);
    }

    /**
     * T3-12 — a failed write must leave nothing behind.
     *
     * <p>The request names a category that does not exist, which is rejected after the command has
     * been built but before anything is committed. What is being proven is not the 404 itself but
     * that the ledger and the balance are exactly as they were.
     */
    @Test
    @DisplayName("T3-12 — lỗi khi ghi giao dịch thì rollback sạch, không có bản ghi một phần")
    void rollsBackCleanlyOnFailure() throws Exception {
        String wallet = createWallet("Ví rollback", 5_000_000L);
        long before = countTransactions();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", 800_000L);
        body.put("walletId", wallet);
        body.put("categoryId", "khong-ton-tai");
        body.put("occurredAt", now());

        mockMvc.perform(authed(post("/api/v1/transactions")).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(countTransactions()).isEqualTo(before);
        assertThat(balanceOf(wallet)).isEqualTo(5_000_000L);
    }

    /**
     * T3-11 — a thousand random transactions, balance exact to the đồng.
     *
     * <p>Written through the API so the number being checked comes from the same SQL aggregate the
     * user sees, and compared against a total accumulated independently in the test.
     */
    @Test
    @DisplayName("T3-11 — 1.000 giao dịch ngẫu nhiên, tổng số dư khớp tuyệt đối")
    void athousandRandomTransactionsBalanceExactly() throws Exception {
        String cash = createWallet("Ví ngẫu nhiên A", 1_234_567L);
        String bank = createWallet("Ví ngẫu nhiên B", 7_654_321L);

        Random random = new Random(20260908L);
        long expectedCash = 1_234_567L;
        long expectedBank = 7_654_321L;

        for (int i = 0; i < 1_000; i++) {
            long amount = 1L + random.nextInt(9_999_999);
            int kind = random.nextInt(3);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amount);
            body.put("walletId", cash);
            body.put("occurredAt", now());

            switch (kind) {
                case 0 -> {
                    body.put("type", "INCOME");
                    body.put("categoryId", SALARY_CATEGORY);
                    expectedCash += amount;
                }
                case 1 -> {
                    body.put("type", "EXPENSE");
                    body.put("categoryId", COFFEE_CATEGORY);
                    expectedCash -= amount;
                }
                default -> {
                    body.put("type", "TRANSFER");
                    body.put("toWalletId", bank);
                    expectedCash -= amount;
                    expectedBank += amount;
                }
            }
            createTransaction(body);
        }

        assertThat(balanceOf(cash))
                .as("số dư ví nguồn phải khớp tuyệt đối sau 1.000 giao dịch")
                .isEqualTo(expectedCash);
        assertThat(balanceOf(bank)).isEqualTo(expectedBank);

        JsonNode wallets = data(mockMvc.perform(authed(get("/api/v1/wallets"))).andReturn());
        long totalAssets = wallets.path("totalAssets").asLong();
        long sumOfBalances = 0L;
        for (JsonNode wallet : wallets.path("wallets")) {
            sumOfBalances += wallet.path("balance").asLong();
        }
        assertThat(totalAssets).isEqualTo(sumOfBalances);
    }

    /**
     * T3-15 — the pie chart has to agree with the list beside it.
     *
     * <p>Dated into a fixed window in the past rather than "this month": every other test in this
     * class writes to the same database, and a summary over the current month would be measuring
     * their transactions too.
     */
    @Test
    @DisplayName("T3-15 — summary groupBy CATEGORY: tổng các nhóm bằng tổng chi trong khoảng")
    void summaryGroupsAddUpToTheTotal() throws Exception {
        String wallet = createWallet("Ví thống kê", 10_000_000L);
        String march = "2019-03-15T10:00:00+07:00";

        createTransaction(dated(expense(wallet, 120_000L, "Cà phê sáng", COFFEE_CATEGORY), march));
        createTransaction(dated(expense(wallet, 380_000L, "Ăn tối", FOOD_CATEGORY), march));
        createTransaction(dated(expense(wallet, 500_000L, "Cà phê họp", COFFEE_CATEGORY), march));
        createTransaction(dated(income(wallet, 9_000_000L, "Lương tháng 3"), march));

        JsonNode summary = data(mockMvc.perform(authed(get("/api/v1/transactions/summary")
                        .param("from", "2019-03-01T00:00:00+07:00")
                        .param("to", "2019-04-01T00:00:00+07:00")
                        .param("groupBy", "CATEGORY")
                        .param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andReturn());

        long groupTotal = 0L;
        double percentageTotal = 0.0;
        for (JsonNode group : summary.path("groups")) {
            groupTotal += group.path("amount").asLong();
            percentageTotal += group.path("percentage").asDouble();
        }

        assertThat(groupTotal).isEqualTo(summary.path("totalExpense").asLong()).isEqualTo(1_000_000L);
        assertThat(percentageTotal).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.2));
        assertThat(summary.path("groups").get(0).path("amount").asLong())
                .as("nhóm lớn nhất đứng đầu để chú giải biểu đồ tròn đọc được")
                .isEqualTo(620_000L);
    }

    @Test
    @DisplayName("Vượt 80% ngân sách trả cảnh báo vàng, vượt 100% trả cảnh báo đỏ")
    void raisesBudgetWarningsAtBothThresholds() throws Exception {
        String wallet = createWallet("Ví ngân sách", 20_000_000L);
        // A category of its own: the budget measures a whole cycle, and the shared seed categories
        // already carry spending from the other tests in this class.
        String parent = createCategory("Ngân sách cha " + System.nanoTime(), null);
        String child = createCategory("Ngân sách con " + System.nanoTime(), parent);
        createBudget(parent, 1_000_000L, "MONTHLY");

        JsonNode belowThreshold = data(mockMvc.perform(authed(post("/api/v1/transactions"))
                        .content(json(expense(wallet, 700_000L, "Đi chợ", parent))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(belowThreshold.has("budgetAlert")).as("dưới 80% thì không có cảnh báo").isFalse();

        JsonNode warning = data(mockMvc.perform(authed(post("/api/v1/transactions"))
                        .content(json(expense(wallet, 150_000L, "Cà phê", child))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(warning.path("budgetAlert").path("level").asText())
                .as("chi ở danh mục con vẫn tính vào ngân sách của danh mục cha")
                .isEqualTo("WARNING");

        JsonNode exceeded = data(mockMvc.perform(authed(post("/api/v1/transactions"))
                        .content(json(expense(wallet, 300_000L, "Ăn nhà hàng", parent))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(exceeded.path("budgetAlert").path("level").asText()).isEqualTo("EXCEEDED");
        assertThat(exceeded.path("budgetAlert").path("spentAmount").asLong()).isEqualTo(1_150_000L);
    }

    /**
     * Regression: a budget on a sub-category used to blow up while rendering the response.
     *
     * <p>The category tree is lazy, and the budget response labels a sub-category with its parent
     * ("Ăn uống › Cà phê"). Nothing loaded the parent before the transaction closed, so the write
     * committed and *then* the mapper threw — the user saw "đã xảy ra lỗi" for a budget that had
     * actually been created, and the next attempt failed with CONFLICT. Every budget in the earlier
     * tests sat on a root category, which never walks that path.
     */
    @Test
    @DisplayName("Ngân sách đặt trên danh mục con trả về được, kèm tên danh mục cha")
    void supportsABudgetOnASubCategory() throws Exception {
        String parent = createCategory("Ngân sách cha con " + System.nanoTime(), null);
        String child = createCategory("Ngân sách con lồng " + System.nanoTime(), parent);

        // Create: the response is rendered from the entity the write just returned.
        JsonNode created = data(mockMvc.perform(authed(post("/api/v1/budgets"))
                        .content(json(Map.of("categoryId", child, "limitAmount", 500_000, "period", "MONTHLY"))))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created.path("category").path("id").asText()).isEqualTo(child);
        assertThat(created.path("category").path("parentName").asText())
                .as("nhãn hiển thị cần tên danh mục cha")
                .isNotBlank();

        // Read back through the list query, which loads the entity a different way.
        JsonNode list = data(mockMvc.perform(authed(get("/api/v1/budgets")))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode found = null;
        for (JsonNode budget : list) {
            if (child.equals(budget.path("category").path("id").asText())) {
                found = budget;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.path("category").path("parentName").asText()).isNotBlank();

        // And through findById, which is a third loading path.
        mockMvc.perform(authed(get("/api/v1/budgets/" + created.path("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.parentName").isNotEmpty());
    }

    /**
     * The duplicate is refused with a clean 409, not a 500.
     *
     * <p>Worth pinning down: the user only ever met this message as the *second* symptom of the
     * lazy loading bug above, so it has to be genuinely reachable and genuinely a conflict.
     */
    @Test
    @DisplayName("Tạo ngân sách trùng danh mục và chu kỳ trả CONFLICT chứ không phải lỗi hệ thống")
    void refusesADuplicateBudgetWithAConflict() throws Exception {
        String category = createCategory("Ngân sách trùng " + System.nanoTime(), null);
        Map<String, Object> body = Map.of("categoryId", category, "limitAmount", 900_000, "period", "MONTHLY");

        mockMvc.perform(authed(post("/api/v1/budgets")).content(json(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post("/api/v1/budgets")).content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Danh mục này đã có ngân sách cùng chu kỳ"));

        // Same category, different period: allowed, so the unique rule is not over-broad.
        mockMvc.perform(authed(post("/api/v1/budgets"))
                        .content(json(Map.of("categoryId", category, "limitAmount", 300_000, "period", "WEEKLY"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Xóa ngân sách rồi tạo lại cùng danh mục và chu kỳ vẫn được")
    void allowsRecreatingABudgetAfterDeletingIt() throws Exception {
        String category = createCategory("Ngân sách tạo lại " + System.nanoTime(), null);
        Map<String, Object> body = Map.of("categoryId", category, "limitAmount", 400_000, "period", "MONTHLY");

        String id = data(mockMvc.perform(authed(post("/api/v1/budgets")).content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();

        mockMvc.perform(authed(delete("/api/v1/budgets/" + id))).andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/v1/budgets")).content(json(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Sửa và xem chi tiết danh mục con không làm vỡ nạp lười")
    void supportsReadingAndEditingASubCategory() throws Exception {
        String parent = createCategory("Cha lười " + System.nanoTime(), null);
        String child = createCategory("Con lười " + System.nanoTime(), parent);

        mockMvc.perform(authed(get("/api/v1/categories/" + child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(parent));

        mockMvc.perform(authed(patch("/api/v1/categories/" + child))
                        .content(json(Map.of("color", "#123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(parent))
                .andExpect(jsonPath("$.data.color").value("#123456"));
    }

    @Test
    @DisplayName("Xóa ví còn giao dịch bị chặn với mã CONFLICT (mục C-6)")
    void refusesToDeleteAWalletThatStillHasTransactions() throws Exception {
        String wallet = createWallet("Ví không xóa được", 500_000L);
        createTransaction(expense(wallet, 50_000L, "Gửi xe"));

        mockMvc.perform(authed(delete("/api/v1/wallets/" + wallet)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Danh mục hệ thống không xóa được, danh mục đang có giao dịch cũng vậy")
    void protectsSystemAndInUseCategories() throws Exception {
        mockMvc.perform(authed(delete("/api/v1/categories/" + FOOD_CATEGORY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        String custom = data(mockMvc.perform(authed(post("/api/v1/categories"))
                        .content(json(Map.of("name", "Danh mục riêng " + System.nanoTime(), "type", "EXPENSE"))))
                .andExpect(status().isCreated())
                .andReturn())
                .path("id")
                .asText();

        String wallet = createWallet("Ví danh mục", 1_000_000L);
        createTransaction(expense(wallet, 20_000L, "Chi lẻ", custom));

        mockMvc.perform(authed(delete("/api/v1/categories/" + custom)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Danh mục trả về dạng cây 2 cấp, có đủ bộ mặc định tiếng Việt (FR-FIN-03)")
    void returnsTheSeededCategoryTree() throws Exception {
        JsonNode tree = data(mockMvc.perform(authed(get("/api/v1/categories").param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode food = null;
        for (JsonNode node : tree) {
            if (FOOD_CATEGORY.equals(node.path("id").asText())) {
                food = node;
            }
        }

        assertThat(food).isNotNull();
        assertThat(food.path("name").asText()).isEqualTo("Ăn uống");
        assertThat(food.path("isSystem").asBoolean()).isTrue();
        assertThat(food.path("children")).hasSize(3);
        for (JsonNode child : food.path("children")) {
            assertThat(child.has("children")).as("cây chỉ sâu 2 cấp").isFalse();
        }
    }

    @Test
    @DisplayName("Lọc theo danh mục cha bao gồm cả giao dịch của danh mục con")
    void filteringByParentCategoryIncludesChildren() throws Exception {
        String wallet = createWallet("Ví lọc", 5_000_000L);
        createTransaction(expense(wallet, 33_000L, "Cà phê lọc test", COFFEE_CATEGORY));

        JsonNode page = data(mockMvc.perform(authed(get("/api/v1/transactions")
                        .param("categoryIds", FOOD_CATEGORY)
                        .param("q", "lọc test")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("category").path("name").asText()).isEqualTo("Cà phê");
    }

    @Test
    @DisplayName("Số dư tại một mốc thời gian bỏ qua giao dịch xảy ra sau mốc đó")
    void balanceAsOfIgnoresLaterTransactions() throws Exception {
        String wallet = createWallet("Ví theo mốc", 1_000_000L);

        OffsetDateTime yesterday = OffsetDateTime.now(ZoneOffset.ofHours(7)).minusDays(1);
        Map<String, Object> old = expense(wallet, 100_000L, "Chi hôm qua");
        old.put("occurredAt", yesterday.toString());
        createTransaction(old);
        createTransaction(expense(wallet, 400_000L, "Chi hôm nay"));

        JsonNode asOf = data(mockMvc.perform(authed(get("/api/v1/wallets/" + wallet + "/balance")
                        .param("asOf", OffsetDateTime.now(ZoneOffset.ofHours(7)).minusHours(1).toString())))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(asOf.path("balance").asLong()).isEqualTo(900_000L);
        assertThat(balanceOf(wallet)).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("Giao dịch định kỳ sinh đúng số giao dịch còn thiếu khi chạy (FR-FIN-13)")
    void recurringRuleGeneratesTransactions() throws Exception {
        String wallet = createWallet("Ví định kỳ", 30_000_000L);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("type", "EXPENSE");
        template.put("amount", 4_500_000L);
        template.put("walletId", wallet);
        template.put("categoryId", "01900000-0000-7000-8000-00000000e301");
        template.put("note", "Tiền nhà hàng tháng");
        template.put("occurredAt", OffsetDateTime.now(ZoneOffset.ofHours(7)).minusMonths(2).toString());

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rrule", "FREQ=MONTHLY;COUNT=3");
        rule.put("startDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(2).toString());
        rule.put("template", template);

        mockMvc.perform(authed(post("/api/v1/recurring-rules")).content(json(rule)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nextRunDate").isNotEmpty());

        JsonNode created = data(mockMvc.perform(authed(post("/api/v1/recurring-rules/run")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(created).as("bù đủ các lần đã tới hạn trong lúc app đóng").hasSize(3);
        for (JsonNode transaction : created) {
            assertThat(transaction.path("source").asText()).isEqualTo("RECURRING");
            assertThat(transaction.path("recurringRuleId").asText()).isNotBlank();
        }
        assertThat(balanceOf(wallet)).isEqualTo(30_000_000L - 3 * 4_500_000L);

        JsonNode rules = data(mockMvc.perform(authed(get("/api/v1/recurring-rules"))).andReturn());
        assertThat(rules.get(0).path("isActive").asBoolean())
                .as("COUNT=3 đã dùng hết thì quy luật tự ngừng")
                .isFalse();
    }

    @Test
    @DisplayName("/bootstrap trả số liệu dashboard của Phase 3 (FR-SYS-01)")
    void bootstrapCarriesTheDashboard() throws Exception {
        String wallet = createWallet("Ví dashboard", 20_000_000L);
        createTransaction(income(wallet, 15_000_000L, "Lương dashboard"));
        createTransaction(expense(wallet, 2_000_000L, "Chi dashboard", FOOD_CATEGORY));

        JsonNode dashboard = data(mockMvc.perform(authed(get("/api/v1/bootstrap")))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("dashboard");

        assertThat(dashboard.isMissingNode()).isFalse();
        assertThat(dashboard.path("monthIncome").asLong()).isGreaterThanOrEqualTo(15_000_000L);
        assertThat(dashboard.path("monthExpense").asLong()).isGreaterThanOrEqualTo(2_000_000L);
        assertThat(dashboard.has("budgetAlerts")).isTrue();
    }

    // ---------- helpers ----------

    private String createWallet(String name, long initialBalance) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", "CASH");
        body.put("initialBalance", initialBalance);
        return data(mockMvc.perform(authed(post("/api/v1/wallets")).content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();
    }

    private void createBudget(String categoryId, long limitAmount, String period) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("limitAmount", limitAmount);
        body.put("period", period);
        body.put("startDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .withDayOfMonth(1)
                .toString());
        mockMvc.perform(authed(post("/api/v1/budgets")).content(json(body)))
                .andExpect(status().isCreated());
    }

    private String createTransaction(Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(authed(post("/api/v1/transactions")).content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("transaction")
                .path("id")
                .asText();
    }

    private String createCategory(String name, String parentId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", "EXPENSE");
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        return data(mockMvc.perform(authed(post("/api/v1/categories")).content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id")
                .asText();
    }

    private Map<String, Object> dated(Map<String, Object> body, String occurredAt) {
        body.put("occurredAt", occurredAt);
        return body;
    }

    private Map<String, Object> expense(String walletId, long amount, String note) {
        return expense(walletId, amount, note, COFFEE_CATEGORY);
    }

    private Map<String, Object> expense(String walletId, long amount, String note, String categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", amount);
        body.put("walletId", walletId);
        body.put("categoryId", categoryId);
        body.put("note", note);
        body.put("occurredAt", now());
        return body;
    }

    private Map<String, Object> income(String walletId, long amount, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "INCOME");
        body.put("amount", amount);
        body.put("walletId", walletId);
        body.put("categoryId", SALARY_CATEGORY);
        body.put("note", note);
        body.put("occurredAt", now());
        return body;
    }

    private long balanceOf(String walletId) throws Exception {
        return data(mockMvc.perform(authed(get("/api/v1/wallets/" + walletId))).andReturn())
                .path("balance")
                .asLong();
    }

    private long countTransactions() throws Exception {
        return data(mockMvc.perform(authed(get("/api/v1/transactions").param("size", "1"))).andReturn())
                .path("totalItems")
                .asLong();
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(7)).toString();
    }
}

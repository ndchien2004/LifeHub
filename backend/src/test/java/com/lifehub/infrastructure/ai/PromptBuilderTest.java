package com.lifehub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.domain.ai.PromptTemplates.Prompt;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The prompt templates load from the classpath and substitute their variables correctly. */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    @DisplayName("Tách đúng phần hệ thống và phần người dùng của template")
    void splitsSystemAndUserSections() {
        Prompt prompt = builder.build("nl-parse", variables());

        assertThat(prompt.system())
                .as("Phần hệ thống mang schema và luật định dạng")
                .contains("Chỉ trả về MỘT object JSON")
                .contains("\"intent\": \"TRANSACTION | TASK | EVENT | UNKNOWN\"");
        assertThat(prompt.user())
                .as("Phần người dùng mang câu và ngữ cảnh")
                .contains("ăn trưa cơm gà 45k");
    }

    @Test
    @DisplayName("Thay đúng mọi biến {{...}} bằng giá trị được truyền vào")
    void substitutesEveryVariable() {
        Prompt prompt = builder.build("nl-parse", variables());

        assertThat(prompt.user())
                .contains("2026-09-11T10:00:00+07:00")
                .contains("Asia/Ho_Chi_Minh")
                .contains("- Ăn uống")
                .contains("- Tiền mặt (mặc định)");
        assertThat(prompt.system() + prompt.user())
                .as("Không được sót placeholder nào chưa thay")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("Biến không được truyền thì thay bằng chuỗi rỗng, không để lại placeholder")
    void replacesMissingVariablesWithEmptyString() {
        Prompt prompt = builder.build("nl-parse", Map.of("text", "cà phê 45k"));

        assertThat(prompt.user()).contains("cà phê 45k").doesNotContain("{{");
    }

    @Test
    @DisplayName("Giá trị chứa ký tự đặc biệt của regex được chèn nguyên văn")
    void insertsValuesLiterally() {
        Prompt prompt = builder.build("nl-parse", Map.of("text", "chi $100 cho C:\\temp"));

        assertThat(prompt.user()).contains("chi $100 cho C:\\temp");
    }

    @Test
    @DisplayName("Template gợi ý danh mục cũng nạp và thay biến được")
    void buildsCategorySuggestTemplate() {
        Prompt prompt = builder.build(
                "category-suggest",
                Map.of("note", "trà sữa gongcha", "type", "Chi (EXPENSE)", "categories", "- Cà phê"));

        assertThat(prompt.system()).contains("\"suggestions\"");
        assertThat(prompt.user()).contains("trà sữa gongcha").contains("Chi (EXPENSE)");
        assertThat(prompt.user()).doesNotContain("{{");
    }

    private static Map<String, String> variables() {
        return Map.of(
                "now", "2026-09-11T10:00:00+07:00",
                "zone", "Asia/Ho_Chi_Minh",
                "weekday", "Thứ Sáu",
                "categories", "- Ăn uống [EXPENSE]",
                "wallets", "- Tiền mặt (mặc định)",
                "projects", "(chưa có)",
                "recentTransactions", "(chưa có)",
                "text", "ăn trưa cơm gà 45k với team",
                "retryHint", "");
    }
}

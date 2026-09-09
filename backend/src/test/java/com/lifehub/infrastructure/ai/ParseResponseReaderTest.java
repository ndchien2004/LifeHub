package com.lifehub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.domain.ai.AiInvalidResponseException;
import com.lifehub.domain.ai.CategorySuggestion;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.CategoryOption;
import com.lifehub.domain.ai.ParseContext.ProjectOption;
import com.lifehub.domain.ai.ParseContext.WalletOption;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.Priority;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T4-16 — schema validation, plus the name-to-id resolution that keeps the model honest. */
class ParseResponseReaderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = LocalDateTime.of(2026, 9, 11, 10, 0)
            .atZone(ZONE)
            .toInstant();

    private final ParseResponseReader reader =
            new ParseResponseReader(new JsonSanitizer(), new ObjectMapper());

    @Test
    @DisplayName("Đọc được giao dịch đầy đủ và nối tên danh mục về đúng id")
    void readsTransactionAndResolvesNames() {
        String json = """
                {
                  "intent": "TRANSACTION",
                  "confidence": 0.94,
                  "transaction": {
                    "type": "EXPENSE",
                    "amount": 45000,
                    "categoryName": "Cà phê",
                    "walletName": "Vietcombank",
                    "note": "Trà sữa",
                    "occurredAt": "2026-09-11T12:00:00+07:00",
                    "fieldConfidence": { "amount": 0.99, "categoryName": 0.87 }
                  },
                  "task": null,
                  "event": null
                }
                """;

        ParseResult result = reader.readParse(json, context());

        assertThat(result.intent()).isEqualTo(ParseIntent.TRANSACTION);
        assertThat(result.source()).isEqualTo(ParseSource.AI);
        assertThat(result.confidence()).isEqualTo(0.94);
        assertThat(result.transaction().type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.transaction().amount()).isEqualTo(45_000L);
        assertThat(result.transaction().categoryId()).isEqualTo("cat-coffee");
        assertThat(result.transaction().categoryName()).isEqualTo("Ăn uống › Cà phê");
        assertThat(result.transaction().walletId()).isEqualTo("wallet-bank");
        assertThat(result.transaction().fieldConfidence())
                .containsEntry("amount", 0.99)
                .containsEntry("categoryName", 0.87);
    }

    @Test
    @DisplayName("Tên danh mục không thuộc về user thì không có id, không tự tạo mới")
    void refusesToInventACategory() {
        String json = """
                {"intent":"TRANSACTION","confidence":0.8,
                 "transaction":{"type":"EXPENSE","amount":45000,"categoryName":"Du thuyền"}}
                """;

        ParseResult result = reader.readParse(json, context());

        assertThat(result.transaction().categoryId()).isNull();
        assertThat(result.transaction().categoryName()).isEqualTo("Du thuyền");
    }

    @Test
    @DisplayName("Không nói ví nào thì dùng ví mặc định của user")
    void fallsBackToDefaultWallet() {
        String json = """
                {"intent":"TRANSACTION","confidence":0.8,
                 "transaction":{"type":"EXPENSE","amount":45000}}
                """;

        assertThat(reader.readParse(json, context()).transaction().walletId())
                .isEqualTo("wallet-cash");
    }

    @Test
    @DisplayName("Số tiền dạng thập phân được làm tròn chính xác, không qua số thực nhị phân")
    void roundsDecimalAmountsExactly() {
        // 45000.5 is not representable in binary floating point; reading it through double would
        // land a đồng either side. The amount goes on to become a Money, where that is a wrong
        // number (FR-FIN-06).
        String json = """
                {"intent":"TRANSACTION","confidence":0.9,
                 "transaction":{"type":"EXPENSE","amount":45000.5}}
                """;

        assertThat(reader.readParse(json, context()).transaction().amount()).isEqualTo(45_001L);
    }

    @Test
    @DisplayName("Số tiền lớn không mất độ chính xác")
    void keepsLargeAmountsExact() {
        String json = """
                {"intent":"TRANSACTION","confidence":0.9,
                 "transaction":{"type":"EXPENSE","amount":9007199254740993}}
                """;

        assertThat(reader.readParse(json, context()).transaction().amount())
                .as("Giá trị này vượt quá khả năng biểu diễn nguyên vẹn của double")
                .isEqualTo(9_007_199_254_740_993L);
    }

    @Test
    @DisplayName("Số tiền gửi dưới dạng chuỗi cũng đọc được")
    void readsAmountsSentAsText() {
        String json = """
                {"intent":"TRANSACTION","confidence":0.9,
                 "transaction":{"type":"EXPENSE","amount":"45000"}}
                """;

        assertThat(reader.readParse(json, context()).transaction().amount()).isEqualTo(45_000L);
    }

    @Test
    @DisplayName("T4-16 — thiếu 'intent' thì ném SchemaValidationException")
    void rejectsMissingIntent() {
        assertThatThrownBy(() -> reader.readParse("{\"confidence\":0.9}", context()))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("intent");
    }

    @Test
    @DisplayName("T4-16 — intent TRANSACTION nhưng thiếu object transaction thì bị từ chối")
    void rejectsMissingPayloadObject() {
        assertThatThrownBy(() ->
                        reader.readParse("{\"intent\":\"TRANSACTION\",\"confidence\":0.9}", context()))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    @DisplayName("T4-16 — giao dịch thiếu số tiền thì bị từ chối")
    void rejectsTransactionWithoutAmount() {
        assertThatThrownBy(() -> reader.readParse(
                        "{\"intent\":\"TRANSACTION\",\"transaction\":{\"type\":\"EXPENSE\"}}", context()))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("số tiền");
    }

    @Test
    @DisplayName("T4-16 — công việc thiếu tiêu đề thì bị từ chối")
    void rejectsTaskWithoutTitle() {
        assertThatThrownBy(() ->
                        reader.readParse("{\"intent\":\"TASK\",\"task\":{\"priority\":\"HIGH\"}}", context()))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("tiêu đề");
    }

    @Test
    @DisplayName("Đọc được công việc và nối tên dự án về id")
    void readsTask() {
        String json = """
                {"intent":"TASK","confidence":0.7,
                 "task":{"title":"Mua quà sinh nhật mẹ","priority":"HIGH",
                         "dueAt":"2026-09-15T17:00:00+07:00","projectName":"Cá nhân"}}
                """;

        ParseResult result = reader.readParse(json, context());

        assertThat(result.task().title()).isEqualTo("Mua quà sinh nhật mẹ");
        assertThat(result.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(result.task().projectId()).isEqualTo("project-personal");
    }

    @Test
    @DisplayName("Sự kiện không có giờ kết thúc thì mặc định kéo dài 1 tiếng")
    void defaultsEventDurationToOneHour() {
        String json = """
                {"intent":"EVENT","confidence":0.9,
                 "event":{"title":"Họp review","startAt":"2026-09-17T14:00:00+07:00",
                          "reminderOffsetMinutes":[15]}}
                """;

        ParseResult result = reader.readParse(json, context());

        assertThat(result.event().startAt().atZone(ZONE).toLocalTime())
                .isEqualTo(LocalTime.of(14, 0));
        assertThat(result.event().endAt().atZone(ZONE).toLocalTime())
                .isEqualTo(LocalTime.of(15, 0));
        assertThat(result.event().reminderOffsetMinutes()).containsExactly(15);
    }

    @Test
    @DisplayName("Khoảng nhắc ngoài danh sách FR-CAL-06 bị loại")
    void dropsUnsupportedReminderOffsets() {
        String json = """
                {"intent":"EVENT","confidence":0.9,
                 "event":{"title":"Họp","startAt":"2026-09-17T14:00:00+07:00",
                          "reminderOffsetMinutes":[7, 15, 999]}}
                """;

        assertThat(reader.readParse(json, context()).event().reminderOffsetMinutes())
                .containsExactly(15);
    }

    @Test
    @DisplayName("Mốc thời gian không có offset được đọc theo múi giờ của user")
    void readsZonelessTimestampInTheUserZone() {
        String json = """
                {"intent":"EVENT","confidence":0.9,
                 "event":{"title":"Họp","startAt":"2026-09-17T14:00:00"}}
                """;

        assertThat(reader.readParse(json, context()).event().startAt().atZone(ZONE).toLocalTime())
                .isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("intent UNKNOWN hợp lệ và không cần object nào")
    void acceptsUnknownIntent() {
        ParseResult result = reader.readParse("{\"intent\":\"UNKNOWN\",\"confidence\":0.1}", context());

        assertThat(result.intent()).isEqualTo(ParseIntent.UNKNOWN);
        assertThat(result.transaction()).isNull();
    }

    @Test
    @DisplayName("Gợi ý danh mục giữ tối đa 3 mục và bỏ tên không tồn tại")
    void readsCategorySuggestions() {
        String json = """
                {"suggestions":[
                  {"categoryName":"Cà phê","confidence":0.9},
                  {"categoryName":"Không tồn tại","confidence":0.8},
                  {"categoryName":"Ăn uống","confidence":0.6},
                  {"categoryName":"Di chuyển","confidence":0.4},
                  {"categoryName":"Lương","confidence":0.2}
                ]}
                """;

        List<CategorySuggestion> suggestions = reader.readCategorySuggestions(json, context());

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.get(0).categoryId()).isEqualTo("cat-coffee");
        assertThat(suggestions.get(0).confidence()).isEqualTo(0.9);
        assertThat(suggestions.get(1).categoryId()).isEqualTo("cat-food");
    }

    @Test
    @DisplayName("Thiếu mảng suggestions thì bị từ chối")
    void rejectsSuggestionsWithoutArray() {
        assertThatThrownBy(() -> reader.readCategorySuggestions("{\"ok\":true}", context()))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("suggestions");
    }

    private static ParseContext context() {
        return new ParseContext(
                NOW,
                ZONE,
                List.of(
                        new CategoryOption("cat-food", "Ăn uống", null, CategoryType.EXPENSE),
                        new CategoryOption("cat-coffee", "Cà phê", "Ăn uống", CategoryType.EXPENSE),
                        new CategoryOption("cat-transport", "Di chuyển", null, CategoryType.EXPENSE),
                        new CategoryOption("cat-salary", "Lương", null, CategoryType.INCOME)),
                List.of(
                        new WalletOption("wallet-cash", "Tiền mặt", true),
                        new WalletOption("wallet-bank", "Vietcombank", false)),
                List.of(new ProjectOption("project-personal", "Cá nhân")),
                List.of());
    }
}

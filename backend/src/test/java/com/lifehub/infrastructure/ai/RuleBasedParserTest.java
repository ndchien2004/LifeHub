package com.lifehub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.CategoryOption;
import com.lifehub.domain.ai.ParseContext.WalletOption;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.Priority;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T4-05 to T4-15 — the offline parser against the specification table in 04-ARCHITECTURE.md 7.
 *
 * <p>The clock is fixed at Friday 2026-09-11 10:00 in Hanoi, so every relative date below is a
 * fixed expectation rather than something that drifts with the calendar.
 */
class RuleBasedParserTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Friday, deliberately: it puts "thứ 5" in the past and "thứ 7" in the future. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    private static final Instant NOW =
            LocalDateTime.of(TODAY, LocalTime.of(10, 0)).atZone(ZONE).toInstant();

    private final RuleBasedParser parser = new RuleBasedParser();

    private ParseResult parse(String text) {
        return parser.parse(text, context());
    }

    @Nested
    @DisplayName("Số tiền")
    class Amounts {

        @Test
        @DisplayName("T4-05 — 45k là 45.000")
        void readsThousandShorthand() {
            assertThat(amountOf("ăn trưa cơm gà 45k với team")).isEqualTo(45_000L);
        }

        @Test
        @DisplayName("T4-06 — 1tr2 là 1.200.000")
        void readsMillionWithTrailingDigit() {
            assertThat(amountOf("1tr2 tiền nhà")).isEqualTo(1_200_000L);
        }

        @Test
        @DisplayName("T4-07 — 2 triệu rưỡi là 2.500.000")
        void readsHalfOfAMillion() {
            assertThat(amountOf("nhận lương 2 triệu rưỡi")).isEqualTo(2_500_000L);
        }

        @Test
        @DisplayName("T4-08 — 3 trăm rưỡi là 350.000")
        void readsHalfOfAHundred() {
            assertThat(amountOf("đổ xăng 3 trăm rưỡi")).isEqualTo(350_000L);
        }

        @Test
        @DisplayName("T4-09 — 500 không hậu tố là 500.000")
        void readsBareNumberAsThousands() {
            assertThat(amountOf("cà phê 500")).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("T4-10 — 1.500.000đ giữ nguyên 1.500.000")
        void readsGroupedNumberWithCurrencyMarker() {
            assertThat(amountOf("trả tiền nhà 1.500.000đ")).isEqualTo(1_500_000L);
        }

        @Test
        @DisplayName("45k5 là 45.500")
        void readsTrailingDigitAfterThousands() {
            assertThat(amountOf("trà sữa 45k5")).isEqualTo(45_500L);
        }

        @Test
        @DisplayName("Số trong 'nhắc trước 15 phút' không phải số tiền")
        void ignoresDurationsWhenLookingForMoney() {
            assertThat(parse("họp 2h chiều nhắc trước 15 phút").intent())
                    .as("Không có số tiền nào trong câu nên đây không thể là giao dịch")
                    .isEqualTo(ParseIntent.EVENT);
        }

        private long amountOf(String text) {
            ParseResult result = parse(text);
            assertThat(result.intent()).isEqualTo(ParseIntent.TRANSACTION);
            return result.transaction().amount();
        }
    }

    @Nested
    @DisplayName("Ngày giờ")
    class DatesAndTimes {

        @Test
        @DisplayName("T4-11 — 'thứ 5 tuần sau' rơi đúng thứ 5 của tuần kế tiếp")
        void resolvesWeekdayInNextWeek() {
            ParseResult result = parse("họp review sprint thứ 5 tuần sau 2h chiều");

            LocalDate date = dateOf(result);
            assertThat(date.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
            assertThat(date).isEqualTo(LocalDate.of(2026, 9, 17));
        }

        @Test
        @DisplayName("T4-12 — 'cuối tháng' là ngày cuối tháng hiện tại")
        void resolvesEndOfMonth() {
            ParseResult result = parse("đóng tiền điện cuối tháng 200k");

            assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        @DisplayName("T4-13 — '2h chiều' là 14:00")
        void resolvesAfternoonHour() {
            ParseResult result = parse("gặp khách 2h chiều");

            assertThat(timeOf(result)).isEqualTo(LocalTime.of(14, 0));
        }

        @Test
        @DisplayName("'9h sáng' là 09:00")
        void resolvesMorningHour() {
            assertThat(timeOf(parse("chạy bộ 9h sáng"))).isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        @DisplayName("'14h30' đọc được cả phút")
        void resolvesTwentyFourHourClockWithMinutes() {
            assertThat(timeOf(parse("phỏng vấn 14h30"))).isEqualTo(LocalTime.of(14, 30));
        }

        @Test
        @DisplayName("'mai' là ngày kế tiếp")
        void resolvesTomorrow() {
            ParseResult result = parse("mua sữa mai");

            assertThat(dateOf(result)).isEqualTo(TODAY.plusDays(1));
        }

        @Test
        @DisplayName("Thứ đã qua trong tuần này thì hiểu là tuần sau")
        void movesAPastWeekdayForward() {
            // Today is Friday, so Thursday this week has already gone.
            ParseResult result = parse("nộp báo cáo thứ 5");

            assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 9, 17));
        }

        @Test
        @DisplayName("'nhắc trước 15 phút' thành reminder 15 phút")
        void readsReminderOffset() {
            ParseResult result = parse("họp 3h chiều nhắc trước 15 phút");

            assertThat(result.event().reminderOffsetMinutes()).containsExactly(15);
        }

        @Test
        @DisplayName("Khoảng nhắc lạ được làm tròn về giá trị form hỗ trợ")
        void snapsUnsupportedReminderOffset() {
            ParseResult result = parse("họp 3h chiều nhắc trước 20 phút");

            assertThat(result.event().reminderOffsetMinutes()).containsExactly(15);
        }

        private LocalDate dateOf(ParseResult result) {
            return instantOf(result).atZone(ZONE).toLocalDate();
        }

        private LocalTime timeOf(ParseResult result) {
            return instantOf(result).atZone(ZONE).toLocalTime();
        }

        private Instant instantOf(ParseResult result) {
            return switch (result.intent()) {
                case TRANSACTION -> result.transaction().occurredAt();
                case EVENT -> result.event().startAt();
                case TASK -> result.task().dueAt();
                case UNKNOWN -> throw new AssertionError("Không nhận diện được ý định của câu");
            };
        }
    }

    @Nested
    @DisplayName("Phân loại")
    class Classification {

        @Test
        @DisplayName("T4-14 — 'lương' là khoản thu")
        void detectsIncomeFromSalary() {
            ParseResult result = parse("nhận lương tháng 9 15 triệu");

            assertThat(result.transaction().type()).isEqualTo(TransactionType.INCOME);
            assertThat(result.transaction().categoryName()).isEqualTo("Lương");
        }

        @Test
        @DisplayName("Mặc định là khoản chi")
        void defaultsToExpense() {
            assertThat(parse("mua bàn phím 800k").transaction().type())
                    .isEqualTo(TransactionType.EXPENSE);
        }

        @Test
        @DisplayName("Từ khóa danh mục được nối tới danh mục thật của user")
        void resolvesCategoryByKeyword() {
            ParseResult result = parse("trà sữa gongcha 45k");

            assertThat(result.transaction().categoryId()).isEqualTo("cat-coffee");
            assertThat(result.transaction().categoryName()).isEqualTo("Ăn uống › Cà phê");
        }

        @Test
        @DisplayName("Ví mặc định được điền sẵn khi câu không nói ví nào")
        void fillsInTheDefaultWallet() {
            assertThat(parse("cà phê 45k").transaction().walletId()).isEqualTo("wallet-cash");
        }

        @Test
        @DisplayName("Câu không có tiền và không có giờ thì là công việc")
        void fallsBackToTask() {
            ParseResult result = parse("mua quà sinh nhật mẹ");

            assertThat(result.intent()).isEqualTo(ParseIntent.TASK);
            assertThat(result.task().title()).isEqualTo("mua quà sinh nhật mẹ");
            assertThat(result.task().priority()).isEqualTo(Priority.MEDIUM);
        }

        @Test
        @DisplayName("Từ 'gấp' nâng độ ưu tiên")
        void raisesPriorityForUrgentWording() {
            assertThat(parse("sửa bug gấp").task().priority()).isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("Tiêu đề bỏ đi phần đã trở thành ngày, giờ và nhắc hẹn")
        void stripsFieldsOutOfTheTitle() {
            ParseResult result = parse("họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút");

            assertThat(result.event().title()).isEqualTo("họp review sprint");
        }
    }

    @Nested
    @DisplayName("An toàn")
    class Safety {

        @Test
        @DisplayName("Chuỗi rỗng trả về UNKNOWN chứ không ném lỗi")
        void neverThrowsOnEmptyInput() {
            assertThat(parser.parse("   ", context()).intent()).isEqualTo(ParseIntent.UNKNOWN);
            assertThat(parser.parse(null, context()).intent()).isEqualTo(ParseIntent.UNKNOWN);
        }

        @Test
        @DisplayName("Kết quả luôn được đánh dấu là do bộ luật sinh ra")
        void alwaysReportsRuleSource() {
            assertThat(parse("cà phê 45k").source()).isEqualTo(ParseSource.RULE);
        }

        @Test
        @DisplayName("Câu không dấu vẫn đọc được")
        void handlesTextWithoutDiacritics() {
            ParseResult result = parse("an trua com ga 45k voi team");

            assertThat(result.intent()).isEqualTo(ParseIntent.TRANSACTION);
            assertThat(result.transaction().amount()).isEqualTo(45_000L);
            assertThat(result.transaction().categoryName()).isEqualTo("Ăn uống");
        }
    }

    /**
     * T4-15 — thirty real Vietnamese sentences, with the intent each one should reach.
     *
     * <p>The plan asks for at least 80% correct. Every row is asserted individually rather than
     * counted, because a corpus test that only reports a percentage tells you nothing about which
     * sentence broke.
     */
    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @MethodSource("corpus")
    @DisplayName("T4-15 — 30 câu tiếng Việt mẫu")
    void classifiesTheSampleCorpus(String sentence, ParseIntent expected) {
        assertThat(parse(sentence).intent()).isEqualTo(expected);
    }

    private static Stream<Arguments> corpus() {
        return Stream.of(
                Arguments.of("ăn trưa cơm gà 45k với team", ParseIntent.TRANSACTION),
                Arguments.of("cà phê sáng 30k", ParseIntent.TRANSACTION),
                Arguments.of("trà sữa gongcha 55k", ParseIntent.TRANSACTION),
                Arguments.of("đổ xăng 100k", ParseIntent.TRANSACTION),
                Arguments.of("grab về nhà 85k", ParseIntent.TRANSACTION),
                Arguments.of("1tr2 tiền nhà", ParseIntent.TRANSACTION),
                Arguments.of("đóng tiền điện 450k", ParseIntent.TRANSACTION),
                Arguments.of("mua bàn phím cơ 1tr5", ParseIntent.TRANSACTION),
                Arguments.of("nhận lương tháng 9 15 triệu", ParseIntent.TRANSACTION),
                Arguments.of("thưởng dự án 2 triệu rưỡi", ParseIntent.TRANSACTION),
                Arguments.of("đi chợ 250k", ParseIntent.TRANSACTION),
                Arguments.of("gửi xe tháng 150k", ParseIntent.TRANSACTION),
                Arguments.of("mua thuốc cảm 85k", ParseIntent.TRANSACTION),
                Arguments.of("vé xem phim 2 người 240k", ParseIntent.TRANSACTION),
                Arguments.of("gia hạn netflix 260k", ParseIntent.TRANSACTION),
                Arguments.of("mua sách 3 trăm rưỡi", ParseIntent.TRANSACTION),
                Arguments.of("ăn tối nhà hàng 1.200.000đ", ParseIntent.TRANSACTION),
                Arguments.of("họp review sprint thứ 5 tuần sau 2h chiều", ParseIntent.EVENT),
                Arguments.of("gặp khách hàng 9h sáng mai", ParseIntent.EVENT),
                Arguments.of("khám răng thứ 7 lúc 10h", ParseIntent.EVENT),
                Arguments.of("standup 9h15 hàng ngày", ParseIntent.EVENT),
                Arguments.of("tiệc sinh nhật 7h tối thứ 6", ParseIntent.EVENT),
                Arguments.of("phỏng vấn ứng viên 14h30", ParseIntent.EVENT),
                Arguments.of("bay đi Đà Nẵng 6h sáng chủ nhật", ParseIntent.EVENT),
                Arguments.of("mua quà sinh nhật mẹ", ParseIntent.TASK),
                Arguments.of("viết báo cáo quý cuối tháng", ParseIntent.TASK),
                Arguments.of("gọi cho anh Nam về hợp đồng", ParseIntent.TASK),
                Arguments.of("dọn nhà cuối tuần", ParseIntent.TASK),
                Arguments.of("đổi bằng lái xe", ParseIntent.TASK),
                Arguments.of("chuẩn bị slide demo gấp", ParseIntent.TASK));
    }

    /** The user's own data: two wallets, a two level category tree, no projects. */
    private static ParseContext context() {
        return new ParseContext(
                NOW,
                ZONE,
                List.of(
                        new CategoryOption("cat-food", "Ăn uống", null, CategoryType.EXPENSE),
                        new CategoryOption("cat-coffee", "Cà phê", "Ăn uống", CategoryType.EXPENSE),
                        new CategoryOption("cat-transport", "Di chuyển", null, CategoryType.EXPENSE),
                        new CategoryOption("cat-fuel", "Xăng xe", "Di chuyển", CategoryType.EXPENSE),
                        new CategoryOption("cat-salary", "Lương", null, CategoryType.INCOME)),
                List.of(
                        new WalletOption("wallet-cash", "Tiền mặt", true),
                        new WalletOption("wallet-bank", "Vietcombank", false)),
                List.of(),
                List.of());
    }
}

package com.lifehub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifehub.domain.ai.AiInvalidResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T4-01 to T4-04 — everything a model wraps around the JSON it was asked for. */
class JsonSanitizerTest {

    private final JsonSanitizer sanitizer = new JsonSanitizer();

    @Test
    @DisplayName("T4-01 — bỏ markdown fence ```json")
    void stripsJsonCodeFence() {
        String raw = "```json\n{\"intent\":\"TASK\"}\n```";

        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"intent\":\"TASK\"}");
    }

    @Test
    @DisplayName("T4-01 — bỏ fence không có tên ngôn ngữ")
    void stripsBareCodeFence() {
        String raw = "```\n{\"intent\":\"EVENT\"}\n```";

        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"intent\":\"EVENT\"}");
    }

    @Test
    @DisplayName("T4-02 — lấy đúng phần JSON khi có chữ thừa trước và sau")
    void extractsJsonSurroundedByProse() {
        String raw = "Chắc chắn rồi! Đây là kết quả:\n{\"intent\":\"TRANSACTION\"}\nHy vọng giúp được bạn.";

        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"intent\":\"TRANSACTION\"}");
    }

    @Test
    @DisplayName("T4-03 — chuỗi không phải JSON thì ném lỗi có cấu trúc")
    void rejectsTextWithNoJson() {
        assertThatThrownBy(() -> sanitizer.sanitize("Mình không hiểu câu này"))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("không phải JSON");
    }

    @Test
    @DisplayName("T4-03 — chuỗi rỗng cũng bị từ chối")
    void rejectsEmptyText() {
        assertThatThrownBy(() -> sanitizer.sanitize("   "))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    @DisplayName("T4-04 — JSON có BOM vẫn parse được")
    void stripsByteOrderMark() {
        String raw = "﻿{\"intent\":\"TASK\"}";

        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"intent\":\"TASK\"}");
    }

    @Test
    @DisplayName("Bỏ ký tự điều khiển lọt vào giữa JSON")
    void stripsControlCharacters() {
        // A literal control character inside a JSON string is illegal JSON and would fail the parse.
        String raw = "{\"note\":\"cơm gà\007\"}";

        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"note\":\"cơm gà\"}");
    }

    @Test
    @DisplayName("Dấu ngoặc nhọn nằm trong chuỗi không làm đóng object sớm")
    void ignoresBracesInsideStringLiterals() {
        String raw = "{\"note\":\"cơm gà {ăn cùng team}\",\"amount\":45000}";

        assertThat(sanitizer.sanitize(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("Dấu ngoặc kép đã escape không kết thúc chuỗi")
    void handlesEscapedQuotes() {
        String raw = "{\"note\":\"quán \\\"Cơm Tấm\\\"\"}";

        assertThat(sanitizer.sanitize(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("JSON thiếu dấu đóng ngoặc bị từ chối")
    void rejectsUnbalancedJson() {
        assertThatThrownBy(() -> sanitizer.sanitize("{\"intent\":\"TASK\""))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("ngoặc");
    }

    @Test
    @DisplayName("Mảng JSON ở ngoài cùng cũng đọc được")
    void extractsTopLevelArray() {
        assertThat(sanitizer.sanitize("Kết quả: [1, 2, 3]")).isEqualTo("[1, 2, 3]");
    }
}

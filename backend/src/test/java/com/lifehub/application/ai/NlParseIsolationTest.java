package com.lifehub.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T4-21, the mandatory security test: <strong>no path from the AI layer can write business data.</strong>
 *
 * <p>AGENTS.md 3.4 rule 1 and the note under SD-02 both say the same thing - a parse result becomes
 * a record only when the user confirms the prefilled form and the frontend issues its own POST. A
 * comment cannot enforce that, so this checks it two ways.
 *
 * <p>The reflective half proves the AI services hold no reference to a writing service, so there is
 * no runtime path to one. The source scan catches the subtler version: a static call, an injected
 * {@code ApplicationContext} lookup, or a new import added by someone who did not read the rule.
 * Either check alone leaves a gap, which is why both are here.
 */
class NlParseIsolationTest {

    private static final Path AI_SOURCE_DIR =
            Path.of("src", "main", "java", "com", "lifehub", "application", "ai");

    /** Services that write business data. None of them may be reachable from the AI layer. */
    private static final Set<String> FORBIDDEN_TYPES = Set.of(
            "TransactionService",
            "TransactionWriter",
            "TaskService",
            "EventService",
            "BudgetService",
            "WalletService",
            "CategoryService",
            "ReminderService",
            "RecurringTransactionService");

    /** Repositories that write business data. The AI layer may only touch its own log. */
    private static final Set<String> FORBIDDEN_REPOSITORIES = Set.of(
            "TransactionRepository",
            "TaskRepository",
            "EventRepository",
            "BudgetRepository",
            "WalletRepository",
            "ReminderRepository",
            "ProjectRepository",
            "CategoryRepository");

    @Test
    @DisplayName("T4-21 — NlParseService không giữ tham chiếu tới bất kỳ service ghi dữ liệu nào")
    void nlParseServiceHoldsNoWritingCollaborator() {
        assertNoForbiddenFields(NlParseService.class);
    }

    @Test
    @DisplayName("T4-21 — CategorySuggestService cũng vậy")
    void categorySuggestServiceHoldsNoWritingCollaborator() {
        assertNoForbiddenFields(CategorySuggestService.class);
    }

    /**
     * The one file allowed to write anything, and the only table it may write to.
     *
     * <p>Every other file in the AI layer is held to "no writes at all"; this one is held to "writes
     * only its own audit row", which the field check below pins down by type.
     */
    private static final String LOG_SERVICE = "AiLogService.java";

    @Test
    @DisplayName("T4-21 — mã nguồn tầng AI không nhắc tới TransactionService.create ở bất cứ đâu")
    void aiSourceNeverCallsATransactionWrite() {
        for (Path file : sourceFiles()) {
            String source = read(file);
            assertThat(source)
                    .as("%s không được gọi thẳng vào tầng ghi dữ liệu", file.getFileName())
                    .doesNotContain("TransactionService")
                    .doesNotContain("TransactionWriter")
                    .doesNotContain(".create(");

            if (!file.getFileName().toString().equals(LOG_SERVICE)) {
                assertThat(source)
                        .as("%s không được ghi bất cứ thứ gì", file.getFileName())
                        .doesNotContain(".save(")
                        .doesNotContain(".delete(");
            }
        }
    }

    @Test
    @DisplayName("T4-21 — AiLogService chỉ ghi được vào đúng bảng nhật ký của nó")
    void logServiceCanOnlyWriteItsOwnTable() {
        List<String> fieldTypes = java.util.Arrays.stream(AiLogService.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType().getSimpleName())
                .toList();

        assertThat(fieldTypes)
                .as("Cộng tác viên duy nhất được phép ghi là repository của chính bảng log")
                .containsExactlyInAnyOrder("AiParseLogRepository", "Clock");
    }

    @Test
    @DisplayName("T4-21 — tầng AI chỉ được đọc, trừ chính bảng log của nó")
    void aiSourceImportsNoWritingRepository() {
        for (Path file : sourceFiles()) {
            String source = read(file);
            for (String repository : FORBIDDEN_REPOSITORIES) {
                boolean writesThroughIt = source.contains(repository + ".save")
                        || source.contains(repository + ".delete");
                assertThat(writesThroughIt)
                        .as("%s không được ghi qua %s", file.getFileName(), repository)
                        .isFalse();
            }
        }
    }

    private void assertNoForbiddenFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            String fieldType = field.getType().getSimpleName();
            assertThat(FORBIDDEN_TYPES)
                    .as("%s giữ tham chiếu tới %s — đó là một đường ghi thẳng vào DB",
                            type.getSimpleName(), fieldType)
                    .doesNotContain(fieldType);
            assertThat(FORBIDDEN_REPOSITORIES)
                    .as("%s giữ tham chiếu tới %s", type.getSimpleName(), fieldType)
                    .doesNotContain(fieldType);
        }
    }

    private List<Path> sourceFiles() {
        try (var files = Files.list(AI_SOURCE_DIR)) {
            List<Path> java = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(java).as("Phải quét được mã nguồn tầng AI").isNotEmpty();
            return java;
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục application/ai", e);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + file, e);
        }
    }
}

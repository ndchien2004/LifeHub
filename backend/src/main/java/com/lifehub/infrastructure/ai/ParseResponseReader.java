package com.lifehub.infrastructure.ai;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.domain.ai.AiInvalidResponseException;
import com.lifehub.domain.ai.AiResponseReader;
import com.lifehub.domain.ai.CategorySuggestion;
import com.lifehub.domain.ai.EventDraft;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseIntent;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.ai.TaskDraft;
import com.lifehub.domain.ai.TransactionDraft;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.Priority;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Steps 4 and 5 of 04-ARCHITECTURE.md 7: sanitise, parse, then hold the result to the schema.
 *
 * <p>The validation is deliberately strict about the few fields a draft is useless without - an
 * intent, an amount for a transaction, a title for a task - and deliberately forgiving about
 * everything else. A missing location, or a confidence the model forgot to emit, is a field the user
 * fills in on a form they are already looking at; refusing the whole answer over it would send them
 * to the offline parser for no gain (UC-09 exception E4).
 *
 * <p>Names are resolved to ids here, against {@link ParseContext}. A name the user does not own
 * resolves to no id and the form shows an empty picker, which is the correct outcome: the model may
 * not invent a category, and it certainly may not point at one by id.
 */
@Component
public class ParseResponseReader implements AiResponseReader {

    /** The six intervals FR-CAL-06 allows. Anything else the model proposes is dropped. */
    private static final Set<Integer> ALLOWED_REMINDER_OFFSETS = Set.of(0, 5, 15, 30, 60, 1440);

    private static final int MAX_SUGGESTIONS = 3;
    private static final double DEFAULT_CONFIDENCE = 0.5;

    private final JsonSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public ParseResponseReader(JsonSanitizer sanitizer, ObjectMapper objectMapper) {
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseResult readParse(String rawText, ParseContext context) {
        JsonNode root = read(rawText);

        ParseIntent intent = enumValue(root.path("intent").asText(null), ParseIntent.class);
        if (intent == null) {
            throw new AiInvalidResponseException("AI trả về JSON thiếu trường 'intent' hợp lệ");
        }

        double confidence = confidenceOf(root.path("confidence"));

        return switch (intent) {
            case TRANSACTION -> ParseResult.of(
                    readTransaction(required(root, "transaction"), context),
                    confidence,
                    ParseSource.AI);
            case TASK -> ParseResult.of(
                    readTask(required(root, "task"), context), confidence, ParseSource.AI);
            case EVENT -> ParseResult.of(
                    readEvent(required(root, "event"), context), confidence, ParseSource.AI);
            case UNKNOWN -> ParseResult.unknown(ParseSource.AI);
        };
    }

    @Override
    public List<CategorySuggestion> readCategorySuggestions(String rawText, ParseContext context) {
        JsonNode suggestions = read(rawText).path("suggestions");
        if (!suggestions.isArray()) {
            throw new AiInvalidResponseException("AI trả về JSON thiếu mảng 'suggestions'");
        }

        List<CategorySuggestion> result = new ArrayList<>();
        for (JsonNode node : suggestions) {
            if (result.size() == MAX_SUGGESTIONS) {
                break;
            }
            String name = text(node, "categoryName");
            double confidence = confidenceOf(node.path("confidence"));
            context.findCategory(name, null)
                    .ifPresent(option -> result.add(
                            new CategorySuggestion(option.id(), option.label(), confidence)));
        }
        return List.copyOf(result);
    }

    private JsonNode read(String rawText) {
        String json = sanitizer.sanitize(rawText);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new AiInvalidResponseException("AI trả về JSON không phải một object");
            }
            return root;
        } catch (JacksonException e) {
            throw new AiInvalidResponseException("AI trả về JSON không đọc được", e);
        }
    }

    private TransactionDraft readTransaction(JsonNode node, ParseContext context) {
        TransactionType type = enumValue(text(node, "type"), TransactionType.class);
        if (type == null) {
            type = TransactionType.EXPENSE;
        }

        Long amount = amountOf(node.path("amount"));
        if (amount == null) {
            throw new AiInvalidResponseException("AI trả về giao dịch thiếu số tiền");
        }

        String categoryName = text(node, "categoryName");
        CategoryType categoryType =
                type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        var category = context.findCategory(categoryName, categoryType);

        String walletName = text(node, "walletName");
        var wallet = context.findWallet(walletName).or(context::defaultWallet);

        return new TransactionDraft(
                type,
                amount,
                category.map(ParseContext.CategoryOption::id).orElse(null),
                category.map(ParseContext.CategoryOption::label).orElse(categoryName),
                wallet.map(ParseContext.WalletOption::id).orElse(null),
                wallet.map(ParseContext.WalletOption::name).orElse(walletName),
                text(node, "note"),
                instantOf(node, "occurredAt", context, context.now()),
                fieldConfidence(node));
    }

    private TaskDraft readTask(JsonNode node, ParseContext context) {
        String title = text(node, "title");
        if (title == null) {
            throw new AiInvalidResponseException("AI trả về công việc thiếu tiêu đề");
        }

        Priority priority = enumValue(text(node, "priority"), Priority.class);
        String projectName = text(node, "projectName");
        var project = context.findProject(projectName);

        return new TaskDraft(
                title,
                priority == null ? Priority.MEDIUM : priority,
                instantOf(node, "dueAt", context, null),
                project.map(ParseContext.ProjectOption::id).orElse(null),
                project.map(ParseContext.ProjectOption::name).orElse(projectName),
                fieldConfidence(node));
    }

    private EventDraft readEvent(JsonNode node, ParseContext context) {
        String title = text(node, "title");
        if (title == null) {
            throw new AiInvalidResponseException("AI trả về sự kiện thiếu tiêu đề");
        }

        Instant startAt = instantOf(node, "startAt", context, null);
        if (startAt == null) {
            throw new AiInvalidResponseException("AI trả về sự kiện thiếu thời gian bắt đầu");
        }

        // A sentence naming a start but no end is the normal case, not a broken answer.
        Instant endAt = instantOf(node, "endAt", context, null);
        if (endAt == null || !endAt.isAfter(startAt)) {
            endAt = startAt.plus(Duration.ofHours(1));
        }

        List<Integer> offsets = new ArrayList<>();
        for (JsonNode offset : node.path("reminderOffsetMinutes")) {
            if (offset.isNumber() && ALLOWED_REMINDER_OFFSETS.contains(offset.asInt())) {
                offsets.add(offset.asInt());
            }
        }

        return new EventDraft(
                title, startAt, endAt, text(node, "location"), offsets, fieldConfidence(node));
    }

    private JsonNode required(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isObject()) {
            throw new AiInvalidResponseException(
                    "AI trả về intent tương ứng nhưng thiếu object '" + field + "'");
        }
        return node;
    }

    private Map<String, Double> fieldConfidence(JsonNode node) {
        JsonNode confidences = node.path("fieldConfidence");
        if (!confidences.isObject()) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        confidences.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNumber()) {
                values.put(entry.getKey(), clamp(entry.getValue().asDouble()));
            }
        });
        return values;
    }

    private double confidenceOf(JsonNode node) {
        return node.isNumber() ? clamp(node.asDouble()) : DEFAULT_CONFIDENCE;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Reads an amount as a whole number of đồng.
     *
     * <p>A model answering {@code 45000.0} is answering correctly in a language with one number
     * type, so a fractional value is rounded rather than rejected. Sub-đồng precision does not exist
     * in this currency and never mattered.
     */
    private Long amountOf(JsonNode node) {
        if (node.isNumber()) {
            return Math.round(node.asDouble());
        }
        if (node.isTextual()) {
            try {
                return Math.round(Double.parseDouble(node.asText().trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads an ISO-8601 timestamp.
     *
     * <p>Accepts a value without an offset by reading it in the user's own zone: a model told "now
     * is 14:00+07:00" that answers "2026-09-10T14:00:00" means two in the afternoon in Hanoi, and
     * reading that as UTC would silently move every appointment by seven hours.
     */
    private Instant instantOf(JsonNode node, String field, ParseContext context, Instant fallback) {
        String value = text(node, field);
        if (value == null) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // Falls through to the zone-less form below.
        }
        try {
            return LocalDateTime.parse(value).atZone(context.zone()).toInstant();
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private <E extends Enum<E>> E enumValue(String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

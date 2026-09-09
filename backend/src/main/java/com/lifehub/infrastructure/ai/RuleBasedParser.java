package com.lifehub.infrastructure.ai;

import com.lifehub.domain.ai.EventDraft;
import com.lifehub.domain.ai.FallbackParser;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.ParseSource;
import com.lifehub.domain.ai.TaskDraft;
import com.lifehub.domain.ai.TransactionDraft;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.task.Priority;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The offline parser (FR-AI-08), built to the specification table in 04-ARCHITECTURE.md 7.
 *
 * <p>Runs whenever AI is switched off, unconfigured, or unreachable. It is not trying to match the
 * model - it is trying to save the user from retyping. Getting the amount and the date right covers
 * most of the value in a sentence like "ăn trưa cơm gà 45k", and the form it prefills is one the
 * user is already looking at and can correct.
 *
 * <p>Never throws. This is the branch reached when something has already failed, and an exception
 * here would turn a degraded feature into a broken one - so a sentence it cannot read becomes
 * {@code UNKNOWN}, which the UI already handles (UC-09 alternate flow 9a).
 *
 * <p>Intent follows the spec exactly: an amount makes it a transaction, a clock time makes it an
 * event, anything else is a task.
 */
@Component
public class RuleBasedParser implements FallbackParser {

    /** Confidence attached to a field the rules actually recognised, rather than defaulted. */
    private static final double MATCHED = 0.8;

    /** Confidence for a field that was guessed from context, above the 0.6 blanking threshold. */
    private static final double INFERRED = 0.65;

    /** Confidence for a field nothing in the sentence supported, deliberately below 0.6. */
    private static final double GUESSED = 0.3;

    /** Overall confidence in a rule based reading: usable, and honestly not an AI answer. */
    private static final double OVERALL = 0.55;

    /** When a sentence names a day but no time, a task falls due at the end of the working day. */
    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(17, 0);

    private static final Pattern URGENT =
            Pattern.compile("(gap|khan|uu tien cao|ngay lap tuc|deadline)");
    private static final Pattern LOW_PRIORITY = Pattern.compile("(khi nao ranh|khong gap|luc nao)");

    /** Date and reminder wording that belongs in a field, not in the title the user reads back. */
    private static final Pattern DATE_PHRASE = Pattern.compile(
            "(hom nay|hom qua|hom kia|ngay mai|ngay kia|ngay mot|tuan sau|tuan toi|cuoi tuan"
                    + "|dau thang|cuoi thang|chu nhat|thu hai|thu bay|thu ba|thu tu|thu nam"
                    + "|thu sau|thu [2-7]|\\bmai\\b)");

    @Override
    public ParseResult parse(String text, ParseContext context) {
        if (text == null || text.isBlank()) {
            return ParseResult.unknown(ParseSource.RULE);
        }

        try {
            return read(text.trim(), context);
        } catch (RuntimeException e) {
            // Last line of defence. The caller is already in a failure path; give it something.
            return ParseResult.unknown(ParseSource.RULE);
        }
    }

    private ParseResult read(String text, ParseContext context) {
        String folded = TextFolding.fold(text);
        ZonedDateTime now = context.now().atZone(context.zone());

        Optional<Long> amount = VietnameseAmountParser.parse(folded);
        Optional<LocalTime> time = VietnameseTimeParser.findTime(folded);
        Optional<LocalDate> date = VietnameseTimeParser.findDate(folded, now);

        if (amount.isPresent()) {
            return ParseResult.of(
                    transaction(text, folded, context, now, amount.get(), date, time),
                    OVERALL,
                    ParseSource.RULE);
        }
        if (time.isPresent()) {
            return ParseResult.of(
                    event(text, folded, now, date, time.get()), OVERALL, ParseSource.RULE);
        }
        return ParseResult.of(task(text, folded, now, date, time), OVERALL, ParseSource.RULE);
    }

    private TransactionDraft transaction(
            String text,
            String folded,
            ParseContext context,
            ZonedDateTime now,
            long amount,
            Optional<LocalDate> date,
            Optional<LocalTime> time) {

        boolean income = CategoryKeywords.looksLikeIncome(folded);
        TransactionType type = income ? TransactionType.INCOME : TransactionType.EXPENSE;
        CategoryType categoryType = income ? CategoryType.INCOME : CategoryType.EXPENSE;

        Optional<String> categoryName = CategoryKeywords.categoryFor(folded, income);
        var category = categoryName.flatMap(name -> context.findCategory(name, categoryType));
        var wallet = context.defaultWallet();

        Instant occurredAt = date
                .map(day -> day.atTime(time.orElse(now.toLocalTime()))
                        .atZone(context.zone())
                        .toInstant())
                .orElse(context.now());

        Map<String, Double> confidence = Map.of(
                "amount", MATCHED,
                "categoryName", category.isPresent() ? INFERRED : GUESSED,
                "occurredAt", date.isPresent() ? MATCHED : INFERRED);

        return new TransactionDraft(
                type,
                amount,
                category.map(ParseContext.CategoryOption::id).orElse(null),
                category.map(ParseContext.CategoryOption::label).orElse(categoryName.orElse(null)),
                wallet.map(ParseContext.WalletOption::id).orElse(null),
                wallet.map(ParseContext.WalletOption::name).orElse(null),
                cleanNote(text, folded, true),
                occurredAt,
                confidence);
    }

    private EventDraft event(
            String text,
            String folded,
            ZonedDateTime now,
            Optional<LocalDate> date,
            LocalTime time) {

        LocalDate day = date.orElseGet(() -> {
            LocalDate today = now.toLocalDate();
            // An hour that has already passed today means tomorrow, not a meeting in the past.
            return time.isBefore(now.toLocalTime()) ? today.plusDays(1) : today;
        });

        Instant startAt = day.atTime(time).atZone(now.getZone()).toInstant();

        Map<String, Double> confidence = Map.of(
                "title", INFERRED,
                "startAt", MATCHED,
                "endAt", GUESSED);

        return new EventDraft(
                cleanNote(text, folded, false),
                startAt,
                startAt.plus(Duration.ofHours(1)),
                null,
                VietnameseTimeParser.findReminderOffsets(folded),
                confidence);
    }

    private TaskDraft task(
            String text,
            String folded,
            ZonedDateTime now,
            Optional<LocalDate> date,
            Optional<LocalTime> time) {

        Instant dueAt = date
                .map(day -> day.atTime(time.orElse(DEFAULT_DUE_TIME))
                        .atZone(now.getZone())
                        .toInstant())
                .orElse(null);

        Map<String, Double> confidence = Map.of(
                "title", MATCHED,
                "priority", GUESSED,
                "dueAt", date.isPresent() ? MATCHED : GUESSED);

        return new TaskDraft(
                cleanNote(text, folded, false), priority(folded), dueAt, null, null, confidence);
    }

    private Priority priority(String folded) {
        if (URGENT.matcher(folded).find()) {
            return Priority.HIGH;
        }
        if (LOW_PRIORITY.matcher(folded).find()) {
            return Priority.LOW;
        }
        return Priority.MEDIUM;
    }

    /**
     * The user's sentence with the parts that became fields taken out.
     *
     * <p>"họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút" should leave "họp review
     * sprint" in the title box, not repeat the whole sentence next to the date and time pickers that
     * now hold the rest of it.
     *
     * <p>Cutting is done by index against the folded text, which is why {@link TextFolding} must
     * preserve length: the ranges found in "thu 5" have to line up with "thứ 5" in the original.
     */
    private String cleanNote(String text, String folded, boolean stripAmount) {
        List<int[]> ranges = new ArrayList<>();
        collect(ranges, VietnameseTimeParser.REMINDER.matcher(folded));
        collect(ranges, VietnameseTimeParser.CLOCK.matcher(folded));
        collect(ranges, DATE_PHRASE.matcher(folded));
        if (stripAmount) {
            collect(ranges, VietnameseAmountParser.AMOUNT.matcher(folded));
        }

        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (!covered(ranges, i)) {
                kept.append(text.charAt(i));
            }
        }

        String cleaned = kept.toString().replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[,.;:\\-]+|[,.;:\\-]+$", "").trim();
        // Everything was a field: better to show the sentence back than an empty title.
        return cleaned.isEmpty() ? text : cleaned;
    }

    private void collect(List<int[]> ranges, Matcher matcher) {
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) {
                ranges.add(new int[] {matcher.start(), matcher.end()});
            }
        }
    }

    private boolean covered(List<int[]> ranges, int index) {
        for (int[] range : ranges) {
            if (index >= range[0] && index < range[1]) {
                return true;
            }
        }
        return false;
    }
}

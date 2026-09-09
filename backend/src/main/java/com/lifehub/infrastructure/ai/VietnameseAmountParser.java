package com.lifehub.infrastructure.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a sum of money out of a Vietnamese sentence, per the specification table in
 * 04-ARCHITECTURE.md 7.
 *
 * <p>Vietnamese writes money in shorthand almost all the time: {@code 45k}, {@code 1tr2},
 * {@code 2 triệu rưỡi}, {@code 3 trăm rưỡi}. Three rules cover the whole table.
 *
 * <ol>
 *   <li>A digit straight after the unit is a tenth of that unit - {@code 1tr2} is 1,2 million.
 *   <li>{@code rưỡi} adds half of the unit just written - {@code 2 triệu rưỡi} is 2,5 million.
 *   <li>A figure under a thousand with no explicit currency marker was written in thousands -
 *       {@code 500} means 500.000, and so does {@code 3 trăm rưỡi} once it has become 350.
 * </ol>
 *
 * <p>Rule 3 is what makes {@code trăm} behave: nobody records a 350 đồng expense, and the
 * acceptance criteria for both {@code 500} and {@code 3 trăm rưỡi} follow from the same line.
 *
 * <p>The arithmetic runs in {@link BigDecimal}. A tenth of a million is exact in decimal and is not
 * in binary, and this value goes on to become a {@code Money} where a one đồng drift is a wrong
 * number (FR-FIN-06).
 */
final class VietnameseAmountParser {

    /**
     * The spec's amount pattern, matched against text that has already been stripped of diacritics.
     *
     * <p>Two orderings are load bearing. The grouped form ({@code 1.500.000}) is tried before the
     * decimal form so its separators read as thousands rather than as a decimal point. And within
     * the unit group, {@code tram} comes before {@code tr} - otherwise "3 trăm" matches "tr" and
     * becomes three million.
     */
    static final Pattern AMOUNT = Pattern.compile(
            "(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*"
                    + "(k|nghin|ngan|trieu|tram|tr|cu|dong|vnd|d)?\\s*(\\d)?");

    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");
    private static final Pattern HALF = Pattern.compile("^\\s*ruoi");

    /** Multiplier per shorthand unit. */
    private static final Map<String, Long> UNITS = Map.ofEntries(
            Map.entry("k", 1_000L),
            Map.entry("nghin", 1_000L),
            Map.entry("ngan", 1_000L),
            Map.entry("tr", 1_000_000L),
            Map.entry("trieu", 1_000_000L),
            Map.entry("cu", 1_000_000L),
            Map.entry("tram", 100L),
            Map.entry("d", 1L),
            Map.entry("dong", 1L),
            Map.entry("vnd", 1L));

    /** Units that state the currency outright, so the figure is already in đồng. */
    private static final java.util.Set<String> EXPLICIT_CURRENCY =
            java.util.Set.of("d", "dong", "vnd");

    /**
     * Words that turn a bare number into something other than money: a duration, a date, a count.
     *
     * <p>Without this, "nhắc trước 15 phút" would book a 15.000 đồng expense and "2h chiều" would
     * book 2.000. The {@code h} branch stops at a letter rather than at a word boundary so that
     * "14h30" is recognised as a clock time - there is no word boundary between {@code h} and
     * {@code 3}, and requiring one silently turned every such time into an amount.
     */
    private static final Pattern NON_MONEY_UNIT = Pattern.compile(
            "^\\s*(?:h(?![a-z])"
                    + "|(?:gio|phut|tieng|ngay|tuan|thang|nam|nguoi|lan|cai|chiec|tuoi)\\b"
                    + "|%)");

    /**
     * Text before a number that makes it an ordinal, a date part, or the minutes of a clock time.
     *
     * <p>The second alternative is what catches the {@code 30} in "14h30". The {@code 14} is already
     * rejected by {@link #NON_MONEY_UNIT}, but the scan then moves past it and finds the minutes
     * standing on their own - which, read as money, would book a 30.000 đồng expense for a meeting.
     */
    private static final Pattern NON_MONEY_PREFIX = Pattern.compile(
            "(?:(?:^|\\s)(?:thu|ngay|thang|nam|luc|khoang|so)\\s*|\\d\\s*(?:h|gio)\\s*)$");

    private VietnameseAmountParser() {
    }

    /**
     * The first sum of money in {@code normalized}, or empty when the sentence names none.
     *
     * @param normalized text already lowercased and stripped of diacritics by
     *     {@link com.lifehub.domain.ai.ParseContext#normalize}
     */
    static Optional<Long> parse(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = AMOUNT.matcher(normalized);
        while (matcher.find()) {
            String unit = matcher.group(2);
            String tail = normalized.substring(matcher.end());

            if (unit == null && !isMoney(normalized, matcher, tail)) {
                continue;
            }

            BigDecimal value = valueOf(matcher, unit, tail);
            if (value.signum() > 0) {
                return Optional.of(value.setScale(0, RoundingMode.HALF_UP).longValueExact());
            }
        }
        return Optional.empty();
    }

    /** A bare number is money unless the words around it say it counts something else. */
    private static boolean isMoney(String normalized, Matcher matcher, String tail) {
        if (NON_MONEY_UNIT.matcher(tail).find()) {
            return false;
        }
        String before = normalized.substring(0, matcher.start());
        return !NON_MONEY_PREFIX.matcher(before).find();
    }

    private static BigDecimal valueOf(Matcher matcher, String unit, String tail) {
        BigDecimal multiplier = BigDecimal.valueOf(unit == null ? 1L : UNITS.getOrDefault(unit, 1L));
        BigDecimal figure = figureOf(matcher.group(1));

        // A digit written straight after the unit is tenths of that unit: 1tr2 is 1,2 million.
        if (matcher.group(3) != null) {
            figure = figure.add(new BigDecimal(matcher.group(3)).movePointLeft(1));
        }

        BigDecimal total = figure.multiply(multiplier);

        // "rưỡi" adds half of the unit just written, not half of the total.
        if (HALF.matcher(tail).find()) {
            total = total.add(multiplier.divide(BigDecimal.valueOf(2)));
        }

        boolean explicitCurrency = unit != null && EXPLICIT_CURRENCY.contains(unit);
        if (!explicitCurrency && total.compareTo(BigDecimal.valueOf(1_000)) < 0) {
            total = total.multiply(BigDecimal.valueOf(1_000));
        }
        return total;
    }

    /** Reads the digits, deciding whether a dot or comma separates thousands or decimals. */
    private static BigDecimal figureOf(String raw) {
        if (GROUPED.matcher(raw).matches()) {
            return new BigDecimal(raw.replace(".", "").replace(",", ""));
        }
        return new BigDecimal(raw.replace(',', '.'));
    }
}

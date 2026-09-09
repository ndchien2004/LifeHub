package com.lifehub.infrastructure.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads dates, clock times and reminder intervals out of a Vietnamese sentence, per the
 * specification table in 04-ARCHITECTURE.md 7.
 *
 * <p>Works on text already lowercased and stripped of diacritics, so "thứ 5" and "thu 5" are the
 * same input. Weeks start on Monday, matching {@code app.week_start} (03-DATA-MODEL.md 2.11).
 */
final class VietnameseTimeParser {

    /** {@code 2h chiều}, {@code 14h30}, {@code 9 giờ sáng}. */
    static final Pattern CLOCK = Pattern.compile(
            "(\\d{1,2})\\s*(?:h|gio)\\s*(\\d{1,2})?\\s*(sang|chieu|toi|dem|trua)?");

    /** {@code nhắc trước 15 phút}, {@code báo trước 1 tiếng}. */
    static final Pattern REMINDER =
            Pattern.compile("(?:nhac|bao)\\s*truoc\\s*(\\d{1,4})\\s*(phut|tieng|gio|ngay)?");

    /**
     * Words that make the number after them a length of time rather than a point in time.
     *
     * <p>"sau" is deliberately absent: "tuần sau 2h chiều" is by far the commoner reading, and
     * treating it as a duration threw away the time in every sentence that named a week.
     */
    private static final Pattern DURATION_PREFIX =
            Pattern.compile("(truoc|trong|keo dai)\\s*$");

    /** Weekday words, keyed by the form that appears in normalised text. */
    private static final Map<String, DayOfWeek> WEEKDAYS = weekdays();

    /** The six intervals FR-CAL-06 allows, in minutes. */
    private static final int[] ALLOWED_OFFSETS = {0, 5, 15, 30, 60, 1440};

    private VietnameseTimeParser() {
    }

    /**
     * The date the sentence points at, or empty when it names none.
     *
     * <p>A weekday on its own means the next one that has not happened yet, so "thứ 5" said on a
     * Friday is next Thursday rather than yesterday. "tuần sau" shifts the whole week first, which
     * is what makes "thứ 5 tuần sau" land on the Thursday of the following week rather than eight
     * days out from whichever Thursday was closest.
     */
    static Optional<LocalDate> findDate(String normalized, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();

        if (normalized.contains("hom qua")) {
            return Optional.of(today.minusDays(1));
        }
        if (normalized.contains("hom kia")) {
            return Optional.of(today.minusDays(2));
        }
        if (normalized.contains("ngay kia") || normalized.contains("ngay mot")) {
            return Optional.of(today.plusDays(2));
        }
        if (normalized.contains("ngay mai") || containsWord(normalized, "mai")) {
            return Optional.of(today.plusDays(1));
        }
        if (normalized.contains("hom nay")) {
            return Optional.of(today);
        }

        Optional<DayOfWeek> weekday = findWeekday(normalized);
        boolean nextWeek = normalized.contains("tuan sau") || normalized.contains("tuan toi");

        if (weekday.isPresent()) {
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (nextWeek) {
                weekStart = weekStart.plusWeeks(1);
            }
            LocalDate target = weekStart.with(TemporalAdjusters.nextOrSame(weekday.get()));
            // A bare weekday already gone by this week means the one coming, not the one past.
            return Optional.of(!nextWeek && target.isBefore(today) ? target.plusWeeks(1) : target);
        }
        if (nextWeek) {
            return Optional.of(today.plusWeeks(1));
        }

        if (normalized.contains("cuoi thang")) {
            return Optional.of(today.with(TemporalAdjusters.lastDayOfMonth()));
        }
        if (normalized.contains("dau thang")) {
            LocalDate first = today.withDayOfMonth(1);
            // "đầu tháng" said on the 20th can only sensibly mean the month coming.
            return Optional.of(first.isBefore(today) ? first.plusMonths(1) : first);
        }
        if (normalized.contains("cuoi tuan")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)));
        }
        return Optional.empty();
    }

    /**
     * The clock time the sentence names, or empty when it names none.
     *
     * <p>"chiều" and "tối" push an hour below noon into the afternoon, so "2h chiều" is 14:00.
     * "12h trưa" stays at noon and "12h đêm" becomes midnight, which is the one place where the
     * twelve hour clock and the twenty four hour clock disagree about the same digits.
     */
    static Optional<LocalTime> findTime(String normalized) {
        Matcher matcher = CLOCK.matcher(normalized);
        while (matcher.find()) {
            Optional<LocalTime> time = timeAt(normalized, matcher);
            if (time.isPresent()) {
                return time;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalTime> timeAt(String normalized, Matcher matcher) {
        // "nhắc trước 1 tiếng" states a duration, not the hour the meeting starts.
        if (DURATION_PREFIX.matcher(normalized.substring(0, matcher.start())).find()) {
            return Optional.empty();
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String period = matcher.group(3);

        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }

        if (period != null) {
            boolean afternoon = period.equals("chieu") || period.equals("toi");
            if (afternoon && hour < 12) {
                hour += 12;
            } else if (period.equals("dem") && hour == 12) {
                hour = 0;
            } else if (period.equals("sang") && hour == 12) {
                hour = 0;
            }
        }
        return Optional.of(LocalTime.of(hour, minute));
    }

    /**
     * Reminder intervals the sentence asks for, snapped to the six values the event form offers.
     *
     * <p>Snapping rather than rejecting: a user who writes "nhắc trước 20 phút" gets the 15 minute
     * option preselected, which is closer to what they asked for than no reminder at all, and which
     * the form can actually display (FR-CAL-06).
     */
    static List<Integer> findReminderOffsets(String normalized) {
        List<Integer> offsets = new ArrayList<>();
        Matcher matcher = REMINDER.matcher(normalized);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            int minutes = switch (unit == null ? "phut" : unit) {
                case "tieng", "gio" -> value * 60;
                case "ngay" -> value * 1440;
                default -> value;
            };
            int snapped = snapToAllowed(minutes);
            if (!offsets.contains(snapped)) {
                offsets.add(snapped);
            }
        }
        return offsets;
    }

    private static int snapToAllowed(int minutes) {
        int best = ALLOWED_OFFSETS[0];
        for (int allowed : ALLOWED_OFFSETS) {
            if (Math.abs(allowed - minutes) < Math.abs(best - minutes)) {
                best = allowed;
            }
        }
        return best;
    }

    private static Optional<DayOfWeek> findWeekday(String normalized) {
        for (Map.Entry<String, DayOfWeek> entry : WEEKDAYS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Weekday spellings in match order.
     *
     * <p>Order is load bearing, and the map has to stay a {@link LinkedHashMap} for that reason.
     * "thu ba" is a prefix of "thu bay", so Saturday has to be tried first or every Saturday would
     * be read as a Tuesday.
     */
    private static Map<String, DayOfWeek> weekdays() {
        Map<String, DayOfWeek> map = new LinkedHashMap<>();
        map.put("chu nhat", DayOfWeek.SUNDAY);
        map.put("thu hai", DayOfWeek.MONDAY);
        map.put("thu bay", DayOfWeek.SATURDAY);
        map.put("thu ba", DayOfWeek.TUESDAY);
        map.put("thu tu", DayOfWeek.WEDNESDAY);
        map.put("thu nam", DayOfWeek.THURSDAY);
        map.put("thu sau", DayOfWeek.FRIDAY);
        map.put("thu 2", DayOfWeek.MONDAY);
        map.put("thu 3", DayOfWeek.TUESDAY);
        map.put("thu 4", DayOfWeek.WEDNESDAY);
        map.put("thu 5", DayOfWeek.THURSDAY);
        map.put("thu 6", DayOfWeek.FRIDAY);
        map.put("thu 7", DayOfWeek.SATURDAY);
        return java.util.Collections.unmodifiableMap(map);
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(word) + "($|\\s)").matcher(text).find();
    }
}

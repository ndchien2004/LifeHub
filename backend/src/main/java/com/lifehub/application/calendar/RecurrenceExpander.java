package com.lifehub.application.calendar;

import com.lifehub.domain.common.ValidationException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fortuna.ical4j.model.Recur;
import org.springframework.stereotype.Component;

/**
 * Turns an RRULE into the concrete moments it produces (SD-04).
 *
 * <p>Expansion always runs in the event's own timezone rather than in UTC. "Every Monday at 09:00"
 * has to stay 09:00 local across a DST boundary, and a rule anchored to a UTC instant would drift
 * by an hour; the machine's zone is fixed here, but the field exists in the schema and honouring it
 * is what keeps a future timezone change from silently moving every repeating event.
 *
 * <p>Every expansion is capped at {@link #MAX_INSTANCES}. That cap is a safety valve, not a
 * paging limit: a rule such as {@code FREQ=SECONDLY} is legal RFC 5545 and would otherwise produce
 * millions of dates for a single month view and hang the request.
 */
@Component
public class RecurrenceExpander {

    /** Hard ceiling on instances returned from one expansion (SD-04). */
    public static final int MAX_INSTANCES = 500;

    /** RFC 5545 form for UNTIL: basic ISO-8601 in UTC, e.g. {@code 20260915T065959Z}. */
    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneId.of("UTC"));

    /**
     * Every start moment the rule produces inside the half-open interval [from, to).
     *
     * <p>Returns the series start alone when there is no rule, which lets callers treat one-off and
     * repeating events through the same path.
     */
    public List<Instant> expand(String rrule, Instant seriesStart, ZoneId zone, Instant from, Instant to) {
        if (rrule == null || rrule.isBlank()) {
            return !seriesStart.isBefore(from) && seriesStart.isBefore(to)
                    ? List.of(seriesStart)
                    : List.of();
        }

        Recur<ZonedDateTime> recur = parse(rrule);
        ZonedDateTime seed = seriesStart.atZone(zone);

        // COUNT is counted from the seed even when the window opens later, so a window starting
        // mid-series returns the tail of the count rather than a fresh one.
        List<ZonedDateTime> dates =
                recur.getDates(seed, from.atZone(zone), to.atZone(zone), MAX_INSTANCES);

        // getDates treats its bounds inclusively; the contract here is the half-open [from, to)
        // that adjacent calendar cells need in order not to render an instance twice.
        List<Instant> occurrences = new ArrayList<>(dates.size());
        for (ZonedDateTime date : dates) {
            Instant instant = date.toInstant();
            if (!instant.isBefore(from) && instant.isBefore(to)) {
                occurrences.add(instant);
            }
        }
        return occurrences;
    }

    /**
     * The first occurrence strictly after {@code after}, if the series has one.
     *
     * <p>Used to roll a repeating task forward on completion (FR-TSK-13) and to top up reminders
     * beyond the generation horizon. Searches in widening windows rather than one huge one so a
     * dense rule does not expand years of dates to find tomorrow.
     */
    public Optional<Instant> nextAfter(String rrule, Instant seriesStart, ZoneId zone, Instant after) {
        if (rrule == null || rrule.isBlank()) {
            return seriesStart.isAfter(after) ? Optional.of(seriesStart) : Optional.empty();
        }

        Instant cursor = after.plusMillis(1);
        for (long days : new long[] {31, 366, 366 * 10}) {
            Instant horizon = cursor.plus(java.time.Duration.ofDays(days));
            List<Instant> found = expand(rrule, seriesStart, zone, cursor, horizon);
            if (!found.isEmpty()) {
                return Optional.of(found.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * The rule the next generated instance should carry (FR-TSK-13).
     *
     * <p>A COUNT is spent one instance at a time, so a task set to repeat five times repeats five
     * times rather than forever - each completion hands its successor a rule one shorter, and the
     * fifth returns empty, which is the signal to stop generating. A rule with no COUNT is passed
     * through unchanged; UNTIL needs no special handling because it is an absolute moment the
     * expander already respects.
     */
    public Optional<String> consumeOne(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            return Optional.empty();
        }
        int count = parse(rrule).getCount();
        if (count <= 0) {
            return Optional.of(rrule);
        }
        if (count <= 1) {
            return Optional.empty();
        }
        // RFC 5545 forbids COUNT and UNTIL together, so stripping both loses nothing here.
        return Optional.of(stripCountAndUntil(rrule) + ";COUNT=" + (count - 1));
    }

    /** Rejects a malformed rule with a field error the form can attach to its RRULE input. */
    public void validate(String rrule) {
        if (rrule != null && !rrule.isBlank()) {
            parse(rrule);
        }
    }

    /**
     * Splits a series at an occurrence, for the THIS_AND_FOLLOWING scope (SD-05, T2-07).
     *
     * <p>The master is closed one second before the cut so the occurrence itself belongs to the new
     * series and no date is produced twice. A COUNT is converted into the number of occurrences
     * that actually remain on each side - leaving the original COUNT on both halves would silently
     * double the length of the series.
     */
    public Split splitAt(String rrule, Instant seriesStart, ZoneId zone, Instant cutStart) {
        Recur<ZonedDateTime> recur = parse(rrule);
        Instant until = cutStart.minusSeconds(1);

        String head = withUntil(stripCountAndUntil(rrule), until);

        String tail = rrule;
        int count = recur.getCount();
        if (count > 0) {
            long consumed = expand(rrule, seriesStart, zone, seriesStart, cutStart).size();
            long remaining = Math.max(count - consumed, 1);
            tail = stripCountAndUntil(rrule) + ";COUNT=" + remaining;
        }
        return new Split(head, tail);
    }

    /** The two rules a split produces: the closed original, and the series carrying on. */
    public record Split(String masterRule, String followingRule) {
    }

    private String withUntil(String rrule, Instant until) {
        return rrule + ";UNTIL=" + UNTIL_FORMAT.format(until);
    }

    /**
     * Drops UNTIL and COUNT, keeping the rest of the rule in its original order.
     *
     * <p>RFC 5545 forbids the two appearing together, so any rule being given a new bound has to
     * have both removed first.
     */
    private String stripCountAndUntil(String rrule) {
        return Arrays.stream(rrule.split(";"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .filter(part -> {
                    String key = part.toUpperCase(Locale.ROOT);
                    return !key.startsWith("UNTIL=") && !key.startsWith("COUNT=");
                })
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private Recur<ZonedDateTime> parse(String rrule) {
        // "RRULE:FREQ=..." is the property form; ical4j parses the value only.
        String value = rrule.trim();
        if (value.regionMatches(true, 0, "RRULE:", 0, 6)) {
            value = value.substring(6).trim();
        }
        try {
            return new Recur<>(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Quy luật lặp không hợp lệ: " + rrule, "rrule");
        }
    }
}

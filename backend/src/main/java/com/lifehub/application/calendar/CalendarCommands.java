package com.lifehub.application.calendar;

import com.lifehub.domain.calendar.EditScope;
import com.lifehub.domain.common.Patch;
import java.time.Instant;
import java.util.List;

/** Input records for the calendar write side (06-API-SPEC.md §5). */
public final class CalendarCommands {

    private CalendarCommands() {
    }

    /**
     * @param reminderOffsets minutes before the event, drawn from {@code Reminder.ALLOWED_OFFSETS}
     *     (FR-CAL-06); an empty list means no reminders
     */
    public record CreateEvent(
            String title,
            String description,
            String location,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String rrule,
            String timezone,
            String taskId,
            List<Integer> reminderOffsets) {
    }

    /** Updates the master row, so every occurrence of a series changes (scope ALL). */
    public record UpdateEvent(
            Patch<String> title,
            Patch<String> description,
            Patch<String> location,
            Patch<Instant> startAt,
            Patch<Instant> endAt,
            Patch<Boolean> allDay,
            Patch<String> rrule,
            Patch<String> timezone,
            Patch<String> taskId,
            Patch<List<Integer>> reminderOffsets) {

        public static UpdateEvent empty() {
            return new UpdateEvent(
                    Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                    Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent());
        }
    }

    /**
     * Edits one occurrence of a series (SD-05).
     *
     * <p>THIS_ONLY writes an {@code event_exception}, which can only carry a new start, end and
     * title (03-DATA-MODEL.md §6, item M-19). The other two scopes accept the full
     * {@link UpdateEvent} payload.
     */
    public record UpdateOccurrence(
            EditScope scope, Instant occurrenceStart, String title, Instant startAt, Instant endAt,
            UpdateEvent fields) {
    }
}

package com.lifehub.api.calendar;

import com.lifehub.api.calendar.CalendarDtos.CalendarItemResponse;
import com.lifehub.api.calendar.CalendarDtos.EventResponse;
import com.lifehub.api.calendar.CalendarDtos.ItemKind;
import com.lifehub.api.calendar.CalendarDtos.ReminderRef;
import com.lifehub.api.calendar.CalendarDtos.ReminderResponse;
import com.lifehub.application.calendar.ReminderNotification;
import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventOccurrence;
import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.task.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Converts calendar domain objects into API responses.
 *
 * <p>Instants become {@link OffsetDateTime} in the display zone, so the frontend receives
 * {@code 2026-09-15T14:00:00+07:00} rather than a bare UTC Z and never has to guess the offset.
 */
@Component
public class CalendarMapper {

    private final Clock clock;
    private final ZoneId displayZone;

    public CalendarMapper(Clock clock, ZoneId displayZone) {
        this.clock = clock;
        this.displayZone = displayZone;
    }

    /**
     * Renders a whole calendar window.
     *
     * <p>Reminders are indexed by the occurrence they belong to before the loop, so attaching them
     * costs one pass rather than a scan of the reminder list per instance.
     */
    public List<CalendarItemResponse> toItems(
            List<EventOccurrence> occurrences,
            List<Task> tasksDue,
            List<Reminder> reminders,
            Set<String> conflictingEventIds) {

        Map<String, List<ReminderRef>> byOccurrence = new HashMap<>();
        for (Reminder reminder : reminders) {
            if (reminder.getEvent() == null) {
                continue;
            }
            byOccurrence
                    .computeIfAbsent(
                            occurrenceKey(reminder.getEvent().getId(), reminder.occurrenceStart()),
                            key -> new ArrayList<>())
                    .add(new ReminderRef(reminder.getId(), reminder.getOffsetMinutes()));
        }

        List<CalendarItemResponse> items = new ArrayList<>(occurrences.size() + tasksDue.size());
        for (EventOccurrence occurrence : occurrences) {
            items.add(toItem(occurrence, byOccurrence, conflictingEventIds));
        }
        for (Task task : tasksDue) {
            items.add(toItem(task));
        }
        items.sort(Comparator.comparing(CalendarItemResponse::startAt));
        return items;
    }

    private CalendarItemResponse toItem(
            EventOccurrence occurrence,
            Map<String, List<ReminderRef>> remindersByOccurrence,
            Set<String> conflictingEventIds) {

        List<ReminderRef> refs = remindersByOccurrence.getOrDefault(
                occurrenceKey(occurrence.eventId(), occurrence.startAt()), List.of());

        return new CalendarItemResponse(
                ItemKind.EVENT,
                occurrence.eventId(),
                toOffset(occurrence.occurrenceStart()),
                occurrence.title(),
                occurrence.description(),
                occurrence.location(),
                toOffset(occurrence.startAt()),
                toOffset(occurrence.endAt()),
                occurrence.allDay(),
                occurrence.recurring(),
                occurrence.exception(),
                conflictingEventIds.contains(occurrence.eventId()),
                occurrence.linkedTaskId(),
                refs.stream().sorted(Comparator.comparingInt(ReminderRef::offsetMinutes)).toList(),
                null,
                null,
                null);
    }

    /**
     * A task deadline drawn on the grid (FR-CAL-10).
     *
     * <p>Given zero length rather than a made-up duration: a deadline is a moment, and the grid
     * renders it as a marker rather than as a block occupying time the user does not actually have
     * committed.
     */
    private CalendarItemResponse toItem(Task task) {
        OffsetDateTime due = toOffset(task.getDueAt());
        return new CalendarItemResponse(
                ItemKind.TASK,
                null,
                due,
                task.getTitle(),
                task.getDescription(),
                null,
                due,
                due,
                false,
                false,
                false,
                false,
                task.getId(),
                List.of(),
                task.getStatus(),
                task.getPriority(),
                task.isOverdue(clock.instant()));
    }

    public EventResponse toResponse(Event event, List<Integer> reminderOffsets) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                toOffset(event.getStartAt()),
                toOffset(event.getEndAt()),
                event.isAllDay(),
                event.getRrule(),
                event.getTimezone(),
                event.getTask() == null ? null : event.getTask().getId(),
                event.getTask() == null ? null : event.getTask().getTitle(),
                reminderOffsets,
                toOffset(event.getCreatedAt()),
                toOffset(event.getUpdatedAt()));
    }

    public ReminderResponse toResponse(Reminder reminder, ReminderNotification rendered) {
        return new ReminderResponse(
                reminder.getId(),
                rendered.title(),
                rendered.body(),
                rendered.refType(),
                rendered.refId(),
                toOffset(reminder.getTriggerAt()),
                toOffset(reminder.occurrenceStart()),
                reminder.getOffsetMinutes(),
                reminder.getStatus(),
                toOffset(reminder.getFiredAt()));
    }

    public OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(displayZone).toOffsetDateTime();
    }

    public Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private String occurrenceKey(String eventId, Instant occurrenceStart) {
        return eventId + "@" + occurrenceStart.toEpochMilli();
    }
}

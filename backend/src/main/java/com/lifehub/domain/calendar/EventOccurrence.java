package com.lifehub.domain.calendar;

import java.time.Duration;
import java.time.Instant;

/**
 * One instance of an event as it appears on the calendar grid (SD-04).
 *
 * <p>A one-off event yields exactly one of these; a repeating event yields one per date the rule
 * produces inside the requested window. Nothing here is persisted.
 *
 * <p>Two fields identify it. {@code eventId} points at the master row, and
 * {@code occurrenceStart} is the slot the rule originally produced - which stays the anchor even
 * when an exception moved the instance to another time, so {@code startAt} and
 * {@code occurrenceStart} can differ. The frontend has to send both back when editing or deleting
 * a single instance.
 */
public record EventOccurrence(
        String eventId,
        Instant occurrenceStart,
        String title,
        String description,
        String location,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        boolean recurring,
        boolean exception,
        String linkedTaskId) {

    /** Builds the unmodified instance a rule produced, shifted from the master template. */
    public static EventOccurrence of(Event event, Instant occurrenceStart) {
        return new EventOccurrence(
                event.getId(),
                occurrenceStart,
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                occurrenceStart,
                occurrenceStart.plus(event.duration()),
                event.isAllDay(),
                event.isRecurring(),
                false,
                event.getTask() == null ? null : event.getTask().getId());
    }

    /** Applies the overrides of an exception row, keeping {@code occurrenceStart} as the anchor. */
    public EventOccurrence withOverrides(EventException override) {
        Instant newStart = override.getNewStartAt() != null ? override.getNewStartAt() : startAt;
        Instant newEnd = override.getNewEndAt() != null
                ? override.getNewEndAt()
                : newStart.plus(Duration.between(startAt, endAt));
        return new EventOccurrence(
                eventId,
                occurrenceStart,
                override.getNewTitle() != null ? override.getNewTitle() : title,
                description,
                location,
                newStart,
                newEnd,
                allDay,
                recurring,
                true,
                linkedTaskId);
    }

    /** Whether this instance overlaps the half-open interval [from, to). */
    public boolean overlaps(Instant from, Instant to) {
        return startAt.isBefore(to) && endAt.isAfter(from);
    }

    /** Whether two instances collide in time, which is what FR-CAL-11 warns about. */
    public boolean collidesWith(EventOccurrence other) {
        return overlaps(other.startAt, other.endAt);
    }
}

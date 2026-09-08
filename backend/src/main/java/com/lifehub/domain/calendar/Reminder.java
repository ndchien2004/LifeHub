package com.lifehub.domain.calendar;

import com.lifehub.domain.common.IdGenerator;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A pending notification for one occurrence of an event, or for a task deadline (FR-CAL-06).
 *
 * <p>One row per (occurrence x configured offset). A repeating event therefore has a row for each
 * of its occurrences inside the generation horizon, refreshed by {@code ReminderService}.
 *
 * <p><b>Which occurrence a row belongs to is derived, not stored.</b> The schema
 * (03-DATA-MODEL.md §2.5) has no column for it, and {@code trigger_at = occurrenceStart -
 * offset_minutes} inverts exactly, so {@link #occurrenceStart()} recovers it. Snoozing keeps that
 * identity true by recomputing {@code offset_minutes} against the new trigger time rather than
 * copying the old one - "minutes before the event" stays literally accurate, and goes negative once
 * the event has started.
 *
 * <p>Does not extend {@code BaseEntity}: the table has {@code created_at} but no
 * {@code updated_at}.
 */
@Entity
@Table(name = "reminder")
public class Reminder {

    /** The offsets FR-CAL-06 allows the user to choose, in minutes before the event. */
    public static final List<Integer> ALLOWED_OFFSETS = List.of(0, 5, 15, 30, 60, 1440);

    /** How long a missed reminder stays actionable before the scheduler expires it (UC-05 step 5). */
    public static final Duration MISSED_WINDOW = Duration.ofHours(24);

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id = IdGenerator.newId();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(name = "trigger_at", nullable = false)
    private Instant triggerAt;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "fired_at")
    private Instant firedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reminder() {
    }

    private Reminder(Event event, Task task, Instant occurrenceStart, int offsetMinutes) {
        this.event = event;
        this.task = task;
        this.offsetMinutes = offsetMinutes;
        this.triggerAt = occurrenceStart.minus(Duration.ofMinutes(offsetMinutes));
    }

    /** A reminder for one occurrence of an event (T2-09). */
    public static Reminder forEvent(Event event, Instant occurrenceStart, int offsetMinutes) {
        requireAllowed(offsetMinutes);
        return new Reminder(Objects.requireNonNull(event), null, occurrenceStart, offsetMinutes);
    }

    /** A reminder for a task deadline (FR-TSK-13 lives next door; this is the notification side). */
    public static Reminder forTask(Task task, Instant dueAt, int offsetMinutes) {
        requireAllowed(offsetMinutes);
        return new Reminder(null, Objects.requireNonNull(task), dueAt, offsetMinutes);
    }

    private static void requireAllowed(int offsetMinutes) {
        if (!ALLOWED_OFFSETS.contains(offsetMinutes)) {
            throw new ValidationException(
                    "Khoảng nhắc trước không hợp lệ. Chọn một trong: " + ALLOWED_OFFSETS,
                    "offsetMinutes");
        }
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * The moment the reminder is about - the start of the occurrence, or the task deadline.
     *
     * <p>Derived rather than stored; see the class comment.
     */
    public Instant occurrenceStart() {
        return triggerAt.plus(Duration.ofMinutes(offsetMinutes));
    }

    /**
     * Whether this row records one of the six offsets the user can configure (FR-CAL-06).
     *
     * <p>The event edit form reads its reminder checkboxes back from these rows, and a snooze
     * writes an ad-hoc offset that is almost never one of the six - so this predicate is what keeps
     * a postponed notification from reappearing as a configured reminder.
     */
    public boolean isConfigured() {
        return ALLOWED_OFFSETS.contains(offsetMinutes);
    }

    public void markFired(Instant now) {
        this.status = ReminderStatus.FIRED;
        this.firedAt = now;
    }

    public void markSnoozed() {
        this.status = ReminderStatus.SNOOZED;
    }

    public void dismiss() {
        this.status = ReminderStatus.DISMISSED;
    }

    public void expire() {
        this.status = ReminderStatus.EXPIRED;
    }

    /**
     * Creates the replacement reminder a snooze produces (UC-04 flow 5a).
     *
     * <p>The offset is recomputed against the new trigger so {@link #occurrenceStart()} still
     * resolves to the same moment; copying the original offset would make the notification quote a
     * time that drifts by exactly the snooze duration every time.
     *
     * <p>{@code offset_minutes} is a whole number of minutes, so the requested trigger is snapped
     * to the nearest minute that keeps the derivation exact. The alternative - storing the trigger
     * verbatim and letting the derived occurrence absorb the remainder - moves the event time the
     * notification announces by up to a minute, which the user would see. Firing up to thirty
     * seconds off a snooze they asked for in whole minutes, they would not, and it is inside the
     * scheduler's own 30 second tick either way (NFR-PERF-06).
     */
    public Reminder snoozeUntil(Instant newTriggerAt) {
        Instant occurrence = occurrenceStart();
        long minutesBefore = Math.round(Duration.between(newTriggerAt, occurrence).toSeconds() / 60.0);
        markSnoozed();
        return new Reminder(event, task, occurrence, (int) minutesBefore);
    }

    /** Whether the trigger time has passed but is still inside the 24 hour actionable window. */
    public boolean isMissedAt(Instant now) {
        return status.isPending()
                && !triggerAt.isAfter(now)
                && triggerAt.isAfter(now.minus(MISSED_WINDOW));
    }

    public String getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Task getTask() {
        return task;
    }

    public Instant getTriggerAt() {
        return triggerAt;
    }

    public int getOffsetMinutes() {
        return offsetMinutes;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Reminder that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

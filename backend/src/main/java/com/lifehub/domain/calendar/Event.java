package com.lifehub.domain.calendar;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A calendar entry (FR-CAL-01, FR-CAL-03).
 *
 * <p>A repeating event is stored as a single master row carrying an RRULE; occurrences are
 * expanded on demand at query time and never written to the table (03-DATA-MODEL.md §2.3). That is
 * what lets the repetition rule be edited without rewriting thousands of rows, and it is why the
 * pair {@code (eventId, occurrenceStart)} - not a row id - identifies one instance to the frontend.
 */
@Entity
@Table(name = "event")
public class Event extends BaseEntity {

    public static final int MAX_TITLE_LENGTH = 255;
    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    /** Optional link to a task (FR-CAL-05). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "rrule")
    private String rrule;

    @Column(name = "timezone", nullable = false)
    private String timezone = DEFAULT_TIMEZONE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Event() {
    }

    public Event(String title, Instant startAt, Instant endAt) {
        retitle(title);
        reschedule(startAt, endAt);
    }

    public void retitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tiêu đề sự kiện không được để trống", "title");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException("Tiêu đề tối đa " + MAX_TITLE_LENGTH + " ký tự", "title");
        }
        this.title = trimmed;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void relocate(String location) {
        this.location = location;
    }

    /**
     * Moves the event, enforcing the {@code end_at > start_at} constraint (T2-08).
     *
     * <p>Checked here rather than only in the database so the caller gets the Vietnamese field
     * error the UI can attach to the right input, instead of a raw constraint violation.
     */
    public void reschedule(Instant startAt, Instant endAt) {
        if (startAt == null) {
            throw new ValidationException("Thiếu thời gian bắt đầu", "startAt");
        }
        if (endAt == null) {
            throw new ValidationException("Thiếu thời gian kết thúc", "endAt");
        }
        if (!endAt.isAfter(startAt)) {
            throw new ValidationException("Thời gian kết thúc phải sau thời gian bắt đầu", "endAt");
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** Shifts the whole event to a new start, preserving its length. */
    public void moveTo(Instant newStart) {
        Duration length = duration();
        reschedule(newStart, newStart.plus(length));
    }

    public void markAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    /** Sets or clears the repetition rule; {@code null} or blank makes the event a one-off. */
    public void repeat(String rrule) {
        this.rrule = rrule == null || rrule.isBlank() ? null : rrule.trim();
    }

    public void inZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            this.timezone = DEFAULT_TIMEZONE;
            return;
        }
        try {
            this.timezone = ZoneId.of(timezone.trim()).getId();
        } catch (Exception e) {
            throw new ValidationException("Múi giờ không hợp lệ: " + timezone, "timezone");
        }
    }

    public void linkTo(Task task) {
        this.task = task;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRecurring() {
        return rrule != null && !rrule.isBlank();
    }

    public Duration duration() {
        return Duration.between(startAt, endAt);
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    /** Whether this event, ignoring recurrence, overlaps the half-open interval [from, to). */
    public boolean overlaps(Instant from, Instant to) {
        return startAt.isBefore(to) && endAt.isAfter(from);
    }

    public Task getTask() {
        return task;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getRrule() {
        return rrule;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

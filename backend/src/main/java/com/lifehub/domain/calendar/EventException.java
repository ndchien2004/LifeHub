package com.lifehub.domain.calendar;

import com.lifehub.domain.common.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One occurrence of a repeating series that has been cancelled or edited on its own (SD-05).
 *
 * <p>Identified by {@code (event_id, original_start_at)}: the original slot the rule produced,
 * which stays the anchor even after the occurrence is moved to another time.
 *
 * <p>Does not extend {@code BaseEntity} - the table has no audit columns
 * (03-DATA-MODEL.md §2.4), and inheriting them would make the mapping disagree with the schema.
 * Only start, end and title can be overridden, which is why the THIS_ONLY scope is limited to
 * those fields (03-DATA-MODEL.md §6, item M-19).
 */
@Entity
@Table(name = "event_exception")
public class EventException {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id = IdGenerator.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "original_start_at", nullable = false)
    private Instant originalStartAt;

    @Column(name = "cancelled", nullable = false)
    private boolean cancelled;

    @Column(name = "new_start_at")
    private Instant newStartAt;

    @Column(name = "new_end_at")
    private Instant newEndAt;

    @Column(name = "new_title")
    private String newTitle;

    protected EventException() {
    }

    public EventException(Event event, Instant originalStartAt) {
        this.event = event;
        this.originalStartAt = originalStartAt;
    }

    /** Marks the occurrence as removed from the series; the master rule is left untouched. */
    public void cancel() {
        this.cancelled = true;
        this.newStartAt = null;
        this.newEndAt = null;
        this.newTitle = null;
    }

    public void overrideTime(Instant newStartAt, Instant newEndAt) {
        this.newStartAt = newStartAt;
        this.newEndAt = newEndAt;
        this.cancelled = false;
    }

    public void overrideTitle(String newTitle) {
        this.newTitle = newTitle == null || newTitle.isBlank() ? null : newTitle.trim();
        this.cancelled = false;
    }

    /** Reassigns to another master, used when a series is split (SD-05, THIS_AND_FOLLOWING). */
    public void moveTo(Event event) {
        this.event = event;
    }

    /** Whether anything is actually overridden - an exception with nothing set is dead weight. */
    public boolean isEmpty() {
        return !cancelled && newStartAt == null && newEndAt == null && newTitle == null;
    }

    public String getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Instant getOriginalStartAt() {
        return originalStartAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Instant getNewStartAt() {
        return newStartAt;
    }

    public Instant getNewEndAt() {
        return newEndAt;
    }

    public String getNewTitle() {
        return newTitle;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventException that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

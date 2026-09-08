package com.lifehub.domain.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for events. Implemented in {@code infrastructure.persistence}. */
public interface EventRepository {

    Event save(Event event);

    /** Looks up a live event; soft deleted rows are invisible here. */
    Optional<Event> findById(String id);

    Optional<Event> findByIdIncludingDeleted(String id);

    /**
     * One-off events overlapping the half-open interval [from, to).
     *
     * <p>Split from the repeating query on purpose: this one is answered entirely by
     * {@code idx_event_range}, while repeating masters have to be expanded in memory (SD-04).
     */
    List<Event> findNonRecurringInRange(Instant from, Instant to);

    /**
     * Repeating masters that could produce an occurrence before {@code to}.
     *
     * <p>No lower bound: a weekly series started years ago still lands inside next month's window.
     * The rule itself decides, and the expander applies the 500 instance cap.
     */
    List<Event> findRecurringStartingBefore(Instant to);

    /** Every live event, ordered by start. Used to seed the reminder generation horizon. */
    List<Event> findAllLive();

    void delete(Event event);
}

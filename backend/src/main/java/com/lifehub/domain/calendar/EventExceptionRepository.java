package com.lifehub.domain.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for the per-occurrence overrides of a repeating series. */
public interface EventExceptionRepository {

    EventException save(EventException exception);

    List<EventException> findByEventId(String eventId);

    /** Overrides for many events in one query, so rendering a month is not N+1 (SD-04). */
    List<EventException> findByEventIds(List<String> eventIds);

    Optional<EventException> findByOccurrence(String eventId, Instant originalStartAt);

    /** Overrides at or after a cut point - the ones a THIS_AND_FOLLOWING split hands over. */
    List<EventException> findFrom(String eventId, Instant originalStartAt);

    void delete(EventException exception);
}

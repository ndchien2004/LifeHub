package com.lifehub.domain.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for reminders. Implemented in {@code infrastructure.persistence}. */
public interface ReminderRepository {

    Reminder save(Reminder reminder);

    List<Reminder> saveAll(List<Reminder> reminders);

    Optional<Reminder> findById(String id);

    /**
     * Reminders whose time has come (SD-03 step 1).
     *
     * <p>Must be served by {@code idx_reminder_pending}: this runs every 30 seconds for the whole
     * life of the process, so a table scan here is a permanent tax.
     */
    List<Reminder> findDue(Instant now);

    /** PENDING reminders whose trigger passed within the last 24 hours (FR-CAL-09, UC-05). */
    List<Reminder> findMissed(Instant since, Instant now);

    /** PENDING reminders left behind by a closed app for more than 24 hours (T2-12). */
    List<Reminder> findExpirable(Instant cutoff);

    List<Reminder> findByEventId(String eventId);

    /** Reminders of many events in one query, so drawing a calendar window is not N+1. */
    List<Reminder> findByEventIds(List<String> eventIds);

    /** Reminders of an event that have not fired yet - the ones a rewrite may safely replace. */
    List<Reminder> findPendingByEventIdFrom(String eventId, Instant from);

    List<Reminder> findByTaskId(String taskId);

    void deleteAll(List<Reminder> reminders);
}

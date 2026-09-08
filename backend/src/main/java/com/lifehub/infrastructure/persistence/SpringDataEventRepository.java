package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data plumbing for the event table.
 *
 * <p>Every read joins the linked task eagerly. {@code open-in-view} is off, so a lazy proxy left
 * unresolved when the transaction ends would explode in the mapper rather than here.
 */
public interface SpringDataEventRepository extends JpaRepository<Event, String> {

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.task WHERE e.id = :id")
    Optional<Event> findByIdWithTask(@Param("id") String id);

    /** One-off events overlapping [from, to). Served by {@code idx_event_range}. */
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.task "
            + "WHERE e.deletedAt IS NULL AND e.rrule IS NULL "
            + "AND e.startAt < :to AND e.endAt > :from "
            + "ORDER BY e.startAt ASC")
    List<Event> findNonRecurringInRange(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Repeating masters that could still produce an occurrence before {@code to}.
     *
     * <p>Deliberately unbounded below: a series started years ago is exactly the one most likely to
     * land in this month's window. The recurrence rule, not the query, decides what it produces.
     */
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.task "
            + "WHERE e.deletedAt IS NULL AND e.rrule IS NOT NULL AND e.startAt < :to "
            + "ORDER BY e.startAt ASC")
    List<Event> findRecurringStartingBefore(@Param("to") Instant to);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.task WHERE e.deletedAt IS NULL ORDER BY e.startAt ASC")
    List<Event> findAllLive();
}

package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.calendar.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data plumbing for the reminder table.
 *
 * <p>The due query runs every 30 seconds for the whole life of the process, so it is written to be
 * answerable from {@code idx_reminder_pending}: an equality on status followed by a range on
 * trigger time, in that column order.
 */
public interface SpringDataReminderRepository extends JpaRepository<Reminder, String> {

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task WHERE r.id = :id")
    Optional<Reminder> findByIdWithRefs(@Param("id") String id);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.status = :status AND r.triggerAt <= :now ORDER BY r.triggerAt ASC")
    List<Reminder> findDue(@Param("status") ReminderStatus status, @Param("now") Instant now);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.status = :status AND r.triggerAt <= :now AND r.triggerAt > :since "
            + "ORDER BY r.triggerAt ASC")
    List<Reminder> findMissed(
            @Param("status") ReminderStatus status,
            @Param("since") Instant since,
            @Param("now") Instant now);

    @Query("SELECT r FROM Reminder r WHERE r.status = :status AND r.triggerAt <= :cutoff")
    List<Reminder> findExpirable(@Param("status") ReminderStatus status, @Param("cutoff") Instant cutoff);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.event.id = :eventId ORDER BY r.triggerAt ASC")
    List<Reminder> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.event.id IN :eventIds ORDER BY r.triggerAt ASC")
    List<Reminder> findByEventIds(@Param("eventIds") List<String> eventIds);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.event.id = :eventId AND r.status = :status AND r.triggerAt >= :from "
            + "ORDER BY r.triggerAt ASC")
    List<Reminder> findPendingByEventIdFrom(
            @Param("eventId") String eventId,
            @Param("status") ReminderStatus status,
            @Param("from") Instant from);

    @Query("SELECT r FROM Reminder r LEFT JOIN FETCH r.event LEFT JOIN FETCH r.task "
            + "WHERE r.task.id = :taskId ORDER BY r.triggerAt ASC")
    List<Reminder> findByTaskId(@Param("taskId") String taskId);
}

package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.EventException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for per-occurrence overrides. */
public interface SpringDataEventExceptionRepository extends JpaRepository<EventException, String> {

    @Query("SELECT x FROM EventException x WHERE x.event.id = :eventId ORDER BY x.originalStartAt ASC")
    List<EventException> findByEventId(@Param("eventId") String eventId);

    /** Overrides for a whole calendar window in one query, so rendering is not N+1 (SD-04). */
    @Query("SELECT x FROM EventException x JOIN FETCH x.event WHERE x.event.id IN :eventIds "
            + "ORDER BY x.originalStartAt ASC")
    List<EventException> findByEventIds(@Param("eventIds") List<String> eventIds);

    @Query("SELECT x FROM EventException x WHERE x.event.id = :eventId AND x.originalStartAt = :originalStartAt")
    Optional<EventException> findByOccurrence(
            @Param("eventId") String eventId, @Param("originalStartAt") Instant originalStartAt);

    @Query("SELECT x FROM EventException x WHERE x.event.id = :eventId AND x.originalStartAt >= :from "
            + "ORDER BY x.originalStartAt ASC")
    List<EventException> findFrom(@Param("eventId") String eventId, @Param("from") Instant from);
}

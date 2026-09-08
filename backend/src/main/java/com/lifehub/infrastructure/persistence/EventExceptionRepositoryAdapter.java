package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link EventExceptionRepository} port. */
@Repository
public class EventExceptionRepositoryAdapter implements EventExceptionRepository {

    private final SpringDataEventExceptionRepository delegate;

    public EventExceptionRepositoryAdapter(SpringDataEventExceptionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public EventException save(EventException exception) {
        return delegate.save(exception);
    }

    @Override
    public List<EventException> findByEventId(String eventId) {
        return delegate.findByEventId(eventId);
    }

    @Override
    public List<EventException> findByEventIds(List<String> eventIds) {
        return eventIds.isEmpty() ? List.of() : delegate.findByEventIds(eventIds);
    }

    @Override
    public Optional<EventException> findByOccurrence(String eventId, Instant originalStartAt) {
        return delegate.findByOccurrence(eventId, originalStartAt);
    }

    @Override
    public List<EventException> findFrom(String eventId, Instant originalStartAt) {
        return delegate.findFrom(eventId, originalStartAt);
    }

    @Override
    public void delete(EventException exception) {
        delegate.delete(exception);
    }
}

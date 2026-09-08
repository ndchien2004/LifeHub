package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link EventRepository} port. */
@Repository
public class EventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository delegate;

    public EventRepositoryAdapter(SpringDataEventRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Event save(Event event) {
        return delegate.save(event);
    }

    @Override
    public Optional<Event> findById(String id) {
        return delegate.findByIdWithTask(id).filter(event -> !event.isDeleted());
    }

    @Override
    public Optional<Event> findByIdIncludingDeleted(String id) {
        return delegate.findByIdWithTask(id);
    }

    @Override
    public List<Event> findNonRecurringInRange(Instant from, Instant to) {
        return delegate.findNonRecurringInRange(from, to);
    }

    @Override
    public List<Event> findRecurringStartingBefore(Instant to) {
        return delegate.findRecurringStartingBefore(to);
    }

    @Override
    public List<Event> findAllLive() {
        return delegate.findAllLive();
    }

    @Override
    public void delete(Event event) {
        delegate.delete(event);
    }
}

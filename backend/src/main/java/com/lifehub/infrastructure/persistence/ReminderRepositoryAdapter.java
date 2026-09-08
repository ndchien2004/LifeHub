package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.calendar.ReminderRepository;
import com.lifehub.domain.calendar.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Adapts Spring Data JPA to the {@link ReminderRepository} port.
 *
 * <p>The PENDING status is supplied here rather than by callers: "due", "missed" and "expirable"
 * are all questions about pending reminders, and letting a caller pass another status would let it
 * ask for something the scheduler must never act on.
 */
@Repository
public class ReminderRepositoryAdapter implements ReminderRepository {

    private final SpringDataReminderRepository delegate;

    public ReminderRepositoryAdapter(SpringDataReminderRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Reminder save(Reminder reminder) {
        return delegate.save(reminder);
    }

    @Override
    public List<Reminder> saveAll(List<Reminder> reminders) {
        return reminders.isEmpty() ? List.of() : delegate.saveAll(reminders);
    }

    @Override
    public Optional<Reminder> findById(String id) {
        return delegate.findByIdWithRefs(id);
    }

    @Override
    public List<Reminder> findDue(Instant now) {
        return delegate.findDue(ReminderStatus.PENDING, now);
    }

    @Override
    public List<Reminder> findMissed(Instant since, Instant now) {
        return delegate.findMissed(ReminderStatus.PENDING, since, now);
    }

    @Override
    public List<Reminder> findExpirable(Instant cutoff) {
        return delegate.findExpirable(ReminderStatus.PENDING, cutoff);
    }

    @Override
    public List<Reminder> findByEventId(String eventId) {
        return delegate.findByEventId(eventId);
    }

    @Override
    public List<Reminder> findByEventIds(List<String> eventIds) {
        return eventIds.isEmpty() ? List.of() : delegate.findByEventIds(eventIds);
    }

    @Override
    public List<Reminder> findPendingByEventIdFrom(String eventId, Instant from) {
        return delegate.findPendingByEventIdFrom(eventId, ReminderStatus.PENDING, from);
    }

    @Override
    public List<Reminder> findByTaskId(String taskId) {
        return delegate.findByTaskId(taskId);
    }

    @Override
    public void deleteAll(List<Reminder> reminders) {
        if (!reminders.isEmpty()) {
            delegate.deleteAll(reminders);
        }
    }
}

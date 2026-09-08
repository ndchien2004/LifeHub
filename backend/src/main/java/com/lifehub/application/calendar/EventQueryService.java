package com.lifehub.application.calendar;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventOccurrence;
import com.lifehub.domain.calendar.EventRepository;
import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.calendar.ReminderRepository;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskFilter;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the calendar module (FR-CAL-02, FR-CAL-10, FR-CAL-11).
 *
 * <p>Answers a window, never "everything": the grid only ever shows one month, week or day, and
 * bounding the query is what keeps a years-old daily series from expanding into thousands of
 * instances on every render (SD-04).
 */
@Service
@Transactional(readOnly = true)
public class EventQueryService {

    /** Widest window a single request may ask for, as a guard against an unbounded range. */
    private static final Duration MAX_RANGE = Duration.ofDays(400);

    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final ReminderRepository reminderRepository;
    private final OccurrenceResolver occurrenceResolver;

    public EventQueryService(
            EventRepository eventRepository,
            TaskRepository taskRepository,
            ReminderRepository reminderRepository,
            OccurrenceResolver occurrenceResolver) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
        this.reminderRepository = reminderRepository;
        this.occurrenceResolver = occurrenceResolver;
    }

    /**
     * Everything the calendar grid draws between {@code from} and {@code to}.
     *
     * @param includeTasks whether task deadlines are overlaid on the grid (FR-CAL-10)
     */
    public CalendarView findInRange(Instant from, Instant to, boolean includeTasks) {
        requireRange(from, to);

        List<EventOccurrence> occurrences = expand(from, to);
        List<Task> tasks = includeTasks ? tasksDueBetween(from, to) : List.of();
        List<String> eventIds =
                occurrences.stream().map(EventOccurrence::eventId).distinct().toList();

        return new CalendarView(
                occurrences,
                tasks,
                reminderRepository.findByEventIds(eventIds),
                conflictingIds(occurrences));
    }

    public Event findById(String id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy sự kiện"));
    }

    /**
     * Existing instances that overlap a proposed slot (FR-CAL-11).
     *
     * <p>Used by the event form before saving, so the warning appears while the user can still act
     * on it. Overlapping is reported, never refused - a double booking is often deliberate.
     *
     * @param excludeEventId the event being edited, so it does not collide with itself
     */
    public List<EventOccurrence> findConflicts(Instant from, Instant to, String excludeEventId) {
        requireRange(from, to);
        return expand(from, to).stream()
                .filter(occurrence -> !occurrence.eventId().equals(excludeEventId))
                .filter(occurrence -> occurrence.overlaps(from, to))
                .sorted(Comparator.comparing(EventOccurrence::startAt))
                .toList();
    }

    private List<EventOccurrence> expand(Instant from, Instant to) {
        List<Event> events = new ArrayList<>(eventRepository.findNonRecurringInRange(from, to));
        events.addAll(eventRepository.findRecurringStartingBefore(to));

        return occurrenceResolver.resolveAll(events, from, to).stream()
                .sorted(Comparator.comparing(EventOccurrence::startAt)
                        .thenComparing(EventOccurrence::title))
                .toList();
    }

    /**
     * Ids of events that have at least one instance overlapping another (FR-CAL-11).
     *
     * <p>Quadratic in principle, linear in practice: the list is already sorted by start, so the
     * scan stops as soon as a later instance begins after the current one ends.
     */
    private Set<String> conflictingIds(List<EventOccurrence> occurrences) {
        Set<String> conflicting = new LinkedHashSet<>();
        for (int i = 0; i < occurrences.size(); i++) {
            EventOccurrence current = occurrences.get(i);
            for (int j = i + 1; j < occurrences.size(); j++) {
                EventOccurrence later = occurrences.get(j);
                if (!later.startAt().isBefore(current.endAt())) {
                    break;
                }
                if (current.collidesWith(later) && !current.allDay() && !later.allDay()) {
                    conflicting.add(current.eventId());
                    conflicting.add(later.eventId());
                }
            }
        }
        return conflicting;
    }

    /** Live, unfinished tasks whose deadline lands in the window (FR-CAL-10). */
    private List<Task> tasksDueBetween(Instant from, Instant to) {
        TaskFilter filter = new TaskFilter(
                null,
                null,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE),
                null,
                from,
                to,
                null,
                false,
                false);
        return taskRepository.search(filter, new PageRequest(0, 500, "dueAt", true)).items();
    }

    private void requireRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new ValidationException("Thiếu khoảng thời gian cần xem", from == null ? "from" : "to");
        }
        if (!to.isAfter(from)) {
            throw new ValidationException("Khoảng thời gian không hợp lệ", "to");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ValidationException(
                    "Khoảng thời gian quá rộng, tối đa " + MAX_RANGE.toDays() + " ngày", "to");
        }
    }

    /**
     * One calendar window: event instances, the task deadlines drawn alongside them, and which
     * events clash.
     */
    public record CalendarView(
            List<EventOccurrence> occurrences,
            List<Task> tasksDue,
            List<Reminder> reminders,
            Set<String> conflictingEventIds) {
    }
}

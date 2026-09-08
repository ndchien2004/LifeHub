package com.lifehub.application.calendar;

import com.lifehub.application.calendar.CalendarCommands.CreateEvent;
import com.lifehub.application.calendar.CalendarCommands.UpdateEvent;
import com.lifehub.application.calendar.CalendarCommands.UpdateOccurrence;
import com.lifehub.domain.calendar.EditScope;
import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import com.lifehub.domain.calendar.EventRepository;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the calendar module (FR-CAL-01, FR-CAL-03 to FR-CAL-05).
 *
 * <p>The hard part is editing one occurrence of a repeating series, which has three meanings
 * (SD-05). Each is a different write: THIS_ONLY adds an override row, THIS_AND_FOLLOWING cuts the
 * series in two, and ALL touches the master. Getting the scope wrong silently rewrites the user's
 * calendar, so the scope is always explicit - there is no default.
 *
 * <p>Every path that can move an occurrence ends by re-synchronising reminders, because a reminder
 * is anchored to an occurrence time that the edit may have just changed.
 */
@Service
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final EventExceptionRepository exceptionRepository;
    private final TaskRepository taskRepository;
    private final ReminderService reminderService;
    private final RecurrenceExpander expander;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            EventExceptionRepository exceptionRepository,
            TaskRepository taskRepository,
            ReminderService reminderService,
            RecurrenceExpander expander,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.exceptionRepository = exceptionRepository;
        this.taskRepository = taskRepository;
        this.reminderService = reminderService;
        this.expander = expander;
        this.clock = clock;
    }

    public Event create(CreateEvent command) {
        expander.validate(command.rrule());

        Event event = new Event(command.title(), command.startAt(), command.endAt());
        event.describe(command.description());
        event.relocate(command.location());
        event.markAllDay(command.allDay());
        event.repeat(command.rrule());
        event.inZone(command.timezone());
        event.linkTo(resolveTask(command.taskId()));

        Event saved = eventRepository.save(event);
        reminderService.syncForEvent(saved, command.reminderOffsets() == null ? List.of() : command.reminderOffsets());
        return saved;
    }

    /** Updates the master row, changing every occurrence of a series (scope ALL). */
    public Event update(String id, UpdateEvent command) {
        Event event = require(id);
        applyToMaster(event, command);
        Event saved = eventRepository.save(event);
        resyncReminders(saved, command.reminderOffsets().orElse(null));
        return saved;
    }

    /**
     * Applies an edit to a single occurrence at the requested scope (SD-05, FR-CAL-04).
     *
     * @return the event the caller should now refer to - a THIS_AND_FOLLOWING split returns the
     *     newly created series, everything else returns the original master
     */
    public Event updateOccurrence(String id, UpdateOccurrence command) {
        Event event = require(id);
        if (command.occurrenceStart() == null) {
            throw new ValidationException("Thiếu mốc thời gian của instance cần sửa", "occurrenceStart");
        }
        EditScope scope = command.scope() == null ? EditScope.THIS_ONLY : command.scope();

        if (!event.isRecurring() && scope != EditScope.ALL) {
            // A one-off event has a single occurrence, so every scope means the same thing.
            // Treating it as ALL avoids writing an override row that nothing would ever read.
            scope = EditScope.ALL;
        }

        return switch (scope) {
            case THIS_ONLY -> updateSingleOccurrence(event, command);
            case THIS_AND_FOLLOWING -> splitSeries(event, command);
            case ALL -> update(id, command.fields() == null ? UpdateEvent.empty() : command.fields());
        };
    }

    /** Soft deletes the whole series (FR-CAL-01). Reminders go with it - they have nothing to fire. */
    public void delete(String id) {
        Event event = require(id);
        event.softDelete(clock.instant());
        eventRepository.save(event);
        reminderService.removeForEvent(id);
    }

    public Event restore(String id) {
        Event event = eventRepository
                .findByIdIncludingDeleted(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sự kiện cần khôi phục"));
        event.restore();
        Event saved = eventRepository.save(event);
        reminderService.syncForEvent(saved, reminderService.configuredOffsets(id));
        return saved;
    }

    /**
     * Removes one occurrence from a series by recording a cancellation (FR-CAL-04, T2-14).
     *
     * <p>The master rule is deliberately left alone: rewriting it to exclude one date would change
     * the meaning of the series, and every other occurrence would have to be re-derived.
     */
    public void deleteOccurrence(String id, Instant occurrenceStart) {
        Event event = require(id);
        if (!event.isRecurring()) {
            delete(id);
            return;
        }
        EventException override = exceptionRepository
                .findByOccurrence(id, occurrenceStart)
                .orElseGet(() -> new EventException(event, occurrenceStart));
        override.cancel();
        exceptionRepository.save(override);
        resyncReminders(event, null);
    }

    private Event updateSingleOccurrence(Event event, UpdateOccurrence command) {
        EventException override = exceptionRepository
                .findByOccurrence(event.getId(), command.occurrenceStart())
                .orElseGet(() -> new EventException(event, command.occurrenceStart()));

        if (command.startAt() != null || command.endAt() != null) {
            Instant newStart = command.startAt() != null ? command.startAt() : command.occurrenceStart();
            Instant newEnd = command.endAt() != null
                    ? command.endAt()
                    : newStart.plus(event.duration());
            if (!newEnd.isAfter(newStart)) {
                throw new ValidationException("Thời gian kết thúc phải sau thời gian bắt đầu", "endAt");
            }
            override.overrideTime(newStart, newEnd);
        }
        if (command.title() != null) {
            override.overrideTitle(command.title());
        }

        if (override.isEmpty()) {
            return event;
        }
        exceptionRepository.save(override);
        resyncReminders(event, null);
        return event;
    }

    /**
     * Cuts a series at an occurrence and starts a new one from it (SD-05, T2-07).
     *
     * <p>The old master is closed with {@code UNTIL} one second before the cut, so the cut
     * occurrence itself belongs to the new series and no date is produced twice. Overrides at or
     * after the cut move across with it; their anchors are absolute instants, which the new series
     * reproduces unchanged.
     */
    private Event splitSeries(Event event, UpdateOccurrence command) {
        Instant cut = command.occurrenceStart();
        RecurrenceExpander.Split split =
                expander.splitAt(event.getRrule(), event.getStartAt(), event.zone(), cut);

        Event following = new Event(event.getTitle(), cut, cut.plus(event.duration()));
        following.describe(event.getDescription());
        following.relocate(event.getLocation());
        following.markAllDay(event.isAllDay());
        following.inZone(event.getTimezone());
        following.linkTo(event.getTask());
        following.repeat(split.followingRule());

        UpdateEvent fields = command.fields() == null ? UpdateEvent.empty() : command.fields();
        applyToMaster(following, fields);
        if (command.title() != null) {
            following.retitle(command.title());
        }
        if (command.startAt() != null) {
            following.reschedule(
                    command.startAt(),
                    command.endAt() != null ? command.endAt() : command.startAt().plus(event.duration()));
        }

        Event saved = eventRepository.save(following);

        for (EventException override : exceptionRepository.findFrom(event.getId(), cut)) {
            override.moveTo(saved);
            exceptionRepository.save(override);
        }

        event.repeat(split.masterRule());
        eventRepository.save(event);

        List<Integer> offsets = reminderService.configuredOffsets(event.getId());
        reminderService.syncForEvent(event, offsets);
        reminderService.syncForEvent(saved, fields.reminderOffsets().orElse(offsets));
        return saved;
    }

    private void applyToMaster(Event event, UpdateEvent command) {
        command.title().ifPresent(event::retitle);
        command.description().ifPresent(event::describe);
        command.location().ifPresent(event::relocate);
        command.allDay().ifPresent(allDay -> event.markAllDay(Boolean.TRUE.equals(allDay)));
        command.timezone().ifPresent(event::inZone);
        command.rrule().ifPresent(rrule -> {
            expander.validate(rrule);
            event.repeat(rrule);
        });
        command.taskId().ifPresent(taskId -> event.linkTo(resolveTask(taskId)));

        // Start and end move together: sending only one of them would leave the pair momentarily
        // inconsistent, and the entity refuses to hold an end before its start.
        boolean movingStart = command.startAt().present() && command.startAt().value() != null;
        boolean movingEnd = command.endAt().present() && command.endAt().value() != null;
        if (movingStart && movingEnd) {
            event.reschedule(command.startAt().value(), command.endAt().value());
        } else if (movingStart) {
            event.moveTo(command.startAt().value());
        } else if (movingEnd) {
            event.reschedule(event.getStartAt(), command.endAt().value());
        }
    }

    /** Regenerates reminders after a change, keeping the configured offsets unless given new ones. */
    private void resyncReminders(Event event, List<Integer> offsets) {
        reminderService.syncForEvent(
                event, offsets != null ? offsets : reminderService.configuredOffsets(event.getId()));
    }

    private Event require(String id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy sự kiện"));
    }

    private Task resolveTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskRepository
                .findById(taskId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy task để liên kết"));
    }
}

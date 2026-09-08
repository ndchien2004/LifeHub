package com.lifehub.application.calendar;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventOccurrence;
import com.lifehub.domain.calendar.Reminder;
import com.lifehub.domain.calendar.ReminderRepository;
import com.lifehub.domain.calendar.ReminderStatus;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Task;
import com.lifehub.domain.task.TaskRepository;
import com.lifehub.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and resolves reminders (FR-CAL-06 to FR-CAL-09).
 *
 * <p>A repeating event does not get one reminder - it gets one per occurrence inside the
 * {@link #HORIZON}, refreshed as the horizon rolls forward. Generating them eagerly is what lets
 * the scheduler answer "what is due now" with a single indexed query every 30 seconds (SD-03)
 * instead of expanding every recurrence rule in the database on every tick.
 */
@Service
@Transactional
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** How far ahead reminders are materialised. */
    public static final Duration HORIZON = Duration.ofDays(90);

    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final OccurrenceResolver occurrenceResolver;
    private final Clock clock;
    private final ZoneId displayZone;

    public ReminderService(
            ReminderRepository reminderRepository,
            TaskRepository taskRepository,
            OccurrenceResolver occurrenceResolver,
            Clock clock,
            ZoneId displayZone) {
        this.reminderRepository = reminderRepository;
        this.taskRepository = taskRepository;
        this.occurrenceResolver = occurrenceResolver;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    /**
     * Fires every reminder whose time has come and reports what to notify about (SD-03, T2-10).
     *
     * <p>Marking as FIRED and building the payload happen in the same transaction, so a reminder is
     * never announced twice: whatever the shell does with the notification afterwards, the next
     * 30 second tick will no longer see this row as pending.
     */
    public List<ReminderNotification> fireDue() {
        List<Reminder> due = reminderRepository.findDue(clock.instant());
        List<ReminderNotification> notifications = new ArrayList<>(due.size());
        for (Reminder reminder : due) {
            markFired(reminder);
            notifications.add(describe(reminder));
        }
        if (!notifications.isEmpty()) {
            log.info("Đã bắn {} nhắc hẹn", notifications.size());
        }
        return notifications;
    }

    /** Renders a reminder as the one line notification the OS shows (06-API-SPEC.md §6). */
    public ReminderNotification describe(Reminder reminder) {
        String time = TIME_OF_DAY.format(reminder.occurrenceStart().atZone(displayZone));

        if (reminder.getEvent() != null) {
            Event event = reminder.getEvent();
            String body = event.getLocation() == null || event.getLocation().isBlank()
                    ? time
                    : time + " · " + event.getLocation();
            return new ReminderNotification(reminder.getId(), event.getTitle(), body, "EVENT", event.getId());
        }

        Task task = reminder.getTask();
        return new ReminderNotification(
                reminder.getId(), task.getTitle(), "Đến hạn lúc " + time, "TASK", task.getId());
    }

    /**
     * Rewrites the reminders of an event to match the given offsets.
     *
     * <p>Only unfired rows are replaced. A reminder that already fired, was dismissed or was
     * snoozed is history and stays put - regenerating it would make an acknowledged notification
     * pop up a second time. Rows carrying an ad-hoc offset are left alone too: those are snooze
     * replacements, not configuration (see {@link Reminder#isConfigured()}).
     *
     * @param offsets minutes before the occurrence; {@code null} leaves the existing set alone
     */
    public void syncForEvent(Event event, List<Integer> offsets) {
        if (offsets == null) {
            return;
        }
        List<Integer> wanted = normalise(offsets);
        Instant now = clock.instant();

        List<Reminder> existing = reminderRepository.findByEventId(event.getId());

        List<Reminder> replaceable = existing.stream()
                .filter(reminder -> reminder.getStatus() == ReminderStatus.PENDING)
                .filter(Reminder::isConfigured)
                .filter(reminder -> !reminder.getTriggerAt().isBefore(now))
                .toList();
        reminderRepository.deleteAll(replaceable);

        Set<String> taken = new HashSet<>();
        for (Reminder reminder : existing) {
            if (!replaceable.contains(reminder)) {
                taken.add(slotKey(reminder.occurrenceStart(), reminder.getOffsetMinutes()));
            }
        }

        if (wanted.isEmpty() || event.isDeleted()) {
            return;
        }

        List<EventOccurrence> occurrences = occurrenceResolver.resolve(event, now, now.plus(HORIZON));

        List<Reminder> created = new ArrayList<>();
        for (EventOccurrence occurrence : occurrences) {
            for (int offset : wanted) {
                if (taken.add(slotKey(occurrence.startAt(), offset))) {
                    created.add(Reminder.forEvent(event, occurrence.startAt(), offset));
                }
            }
        }
        reminderRepository.saveAll(created);
    }

    /** The offsets the user configured for an event, for the edit form (FR-CAL-06). */
    @Transactional(readOnly = true)
    public List<Integer> configuredOffsets(String eventId) {
        return reminderRepository.findByEventId(eventId).stream()
                .filter(Reminder::isConfigured)
                .map(Reminder::getOffsetMinutes)
                .distinct()
                .sorted()
                .toList();
    }

    /** Drops every reminder of an event, used when the event itself goes away. */
    public void removeForEvent(String eventId) {
        reminderRepository.deleteAll(reminderRepository.findByEventId(eventId));
    }

    /** Reminders that came due while the app was closed, within the last 24 hours (FR-CAL-09). */
    @Transactional(readOnly = true)
    public List<Reminder> findMissed() {
        Instant now = clock.instant();
        return reminderRepository.findMissed(now.minus(Reminder.MISSED_WINDOW), now).stream()
                .sorted(Comparator.comparing(Reminder::getTriggerAt))
                .toList();
    }

    /**
     * Retires reminders nobody can act on any more (UC-05 step 5, T2-12).
     *
     * @return how many were expired, so the behaviour is assertable
     */
    public int expireStale() {
        Instant cutoff = clock.instant().minus(Reminder.MISSED_WINDOW);
        List<Reminder> stale = reminderRepository.findExpirable(cutoff);
        for (Reminder reminder : stale) {
            reminder.expire();
            reminderRepository.save(reminder);
        }
        if (!stale.isEmpty()) {
            log.info("Đã đánh dấu {} nhắc hẹn quá hạn là EXPIRED", stale.size());
        }
        return stale.size();
    }

    /** Postpones a reminder, marking the original SNOOZED (UC-04 flow 5a, T2-11). */
    public Reminder snooze(String id, int minutes) {
        if (minutes <= 0) {
            throw new ValidationException("Thời gian hoãn phải lớn hơn 0 phút", "minutes");
        }
        Reminder reminder = require(id);
        if (reminder.getStatus().isClosed()) {
            throw new ValidationException("Nhắc hẹn này đã kết thúc, không hoãn được", "status");
        }
        Reminder replacement = reminder.snoozeUntil(clock.instant().plus(Duration.ofMinutes(minutes)));
        reminderRepository.save(reminder);
        return reminderRepository.save(replacement);
    }

    /**
     * Acknowledges a reminder (UC-04 flow 5b).
     *
     * <p>When it belongs to a task, acknowledging also completes the task - that is what the
     * "Đã xong" button on the notification means to the user, and SD-03 makes it part of this step.
     */
    public Reminder dismiss(String id) {
        Reminder reminder = require(id);
        reminder.dismiss();
        completeLinkedTask(reminder);
        return reminderRepository.save(reminder);
    }

    /** Clears the whole missed list in one action, from the startup modal (UC-05). */
    public int dismissAll() {
        List<Reminder> missed = findMissed();
        for (Reminder reminder : missed) {
            reminder.dismiss();
            reminderRepository.save(reminder);
        }
        return missed.size();
    }

    /** Marks a reminder as delivered. Called by the scheduler after the push succeeds (SD-03). */
    public Reminder markFired(Reminder reminder) {
        reminder.markFired(clock.instant());
        return reminderRepository.save(reminder);
    }

    private void completeLinkedTask(Reminder reminder) {
        Task task = reminder.getTask();
        if (task == null) {
            return;
        }
        task.changeStatus(TaskStatus.DONE, clock.instant());
        taskRepository.save(task);
    }

    private Reminder require(String id) {
        return reminderRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhắc hẹn"));
    }

    /** Sorted, de-duplicated, and validated against the six offsets FR-CAL-06 allows. */
    private List<Integer> normalise(List<Integer> offsets) {
        Set<Integer> unique = new TreeSet<>();
        for (Integer offset : offsets) {
            if (offset == null) {
                continue;
            }
            if (!Reminder.ALLOWED_OFFSETS.contains(offset)) {
                throw new ValidationException(
                        "Khoảng nhắc trước không hợp lệ: " + offset + " phút", "reminderOffsets");
            }
            unique.add(offset);
        }
        return List.copyOf(unique);
    }

    private String slotKey(Instant occurrenceStart, int offsetMinutes) {
        return occurrenceStart.toEpochMilli() + "@" + offsetMinutes;
    }
}

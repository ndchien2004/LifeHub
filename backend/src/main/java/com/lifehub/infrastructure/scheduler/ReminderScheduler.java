package com.lifehub.infrastructure.scheduler;

import com.lifehub.application.calendar.ReminderNotification;
import com.lifehub.application.calendar.ReminderNotifier;
import com.lifehub.application.calendar.ReminderService;
import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the two background jobs the calendar needs (SD-03).
 *
 * <p>The fast loop asks every 30 seconds which reminders are due and pushes them. That interval is
 * what NFR-PERF-06 buys: a reminder is never more than 30 seconds late, and the query behind it is
 * a single indexed lookup, so the cost of running it forever is negligible.
 *
 * <p>The slow loop rolls the 90 day generation horizon forward. Without it, a weekly series would
 * quietly stop reminding three months after it was created - the rows simply would not exist.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    /** SD-03: one tick every 30 seconds. */
    static final long TICK_MILLIS = 30_000L;

    private final ReminderService reminderService;
    private final ReminderNotifier notifier;
    private final EventRepository eventRepository;

    public ReminderScheduler(
            ReminderService reminderService, ReminderNotifier notifier, EventRepository eventRepository) {
        this.reminderService = reminderService;
        this.notifier = notifier;
        this.eventRepository = eventRepository;
    }

    /** Fires everything that has come due and pushes it to the shell. */
    @Scheduled(fixedRate = TICK_MILLIS)
    public void tick() {
        try {
            List<ReminderNotification> fired = reminderService.fireDue();
            notifier.publishAll(fired);
        } catch (RuntimeException e) {
            // A scheduled method that throws is silently dropped by Spring and the whole loop
            // keeps running with no trace of the failure, so it is logged here explicitly.
            log.error("Vòng quét nhắc hẹn thất bại", e);
        }
    }

    /**
     * Extends the reminder horizon and retires reminders nobody can act on (UC-05 step 5).
     *
     * <p>Runs at startup as well as hourly: the app may have been closed for weeks, and both the
     * horizon and the 24 hour expiry window need catching up before the user sees the missed
     * reminder modal.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 3_600_000L, initialDelay = 3_600_000L)
    public void refreshHorizon() {
        try {
            reminderService.expireStale();
            topUpReminders();
        } catch (RuntimeException e) {
            log.error("Không làm mới được danh sách nhắc hẹn", e);
        }
    }

    @Transactional
    void topUpReminders() {
        int touched = 0;
        for (Event event : eventRepository.findAllLive()) {
            List<Integer> offsets = reminderService.configuredOffsets(event.getId());
            if (!offsets.isEmpty()) {
                reminderService.syncForEvent(event, offsets);
                touched++;
            }
        }
        if (touched > 0) {
            log.info("Đã làm mới nhắc hẹn cho {} sự kiện trong {} ngày tới",
                    touched, ReminderService.HORIZON.toDays());
        }
    }
}

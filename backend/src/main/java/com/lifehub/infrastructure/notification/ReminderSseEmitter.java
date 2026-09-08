package com.lifehub.infrastructure.notification;

import com.lifehub.application.calendar.ReminderNotification;
import com.lifehub.application.calendar.ReminderNotifier;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes fired reminders to whoever is listening on {@code GET /api/v1/events/stream}
 * (04-ARCHITECTURE.md §6.2).
 *
 * <p>Server-Sent Events rather than polling: a reminder has to arrive within 30 seconds
 * (NFR-PERF-06), and the alternative is the renderer asking every few seconds forever for an answer
 * that is almost always "nothing".
 *
 * <p>Normally there are one or two subscribers - the Electron main process, which owns the native
 * notifications, and the renderer, which shows the in-app fallback when the OS blocks them. The
 * list is copy-on-write because it is read by the scheduler thread while HTTP threads add and
 * remove entries.
 */
@Component
public class ReminderSseEmitter implements ReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReminderSseEmitter.class);

    /**
     * How long a subscription lives before the client is expected to reconnect.
     *
     * <p>Not a keep-alive: SSE clients reconnect on their own, and a bounded lifetime is what stops
     * a half-open connection from being held for the entire run of the app.
     */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    /** Registers a new listener and returns the emitter Spring MVC should keep open. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        subscribers.add(emitter);

        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));

        try {
            // An immediate comment flushes the response headers, so the client knows it is
            // connected rather than waiting for the first reminder, which may be hours away.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            subscribers.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public void publish(ReminderNotification notification) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name(ReminderNotification.EVENT_TYPE)
                        .data(notification));
            } catch (IOException | IllegalStateException e) {
                // A client that went away mid-push is normal - the window was closed, or the shell
                // is restarting. Drop it and carry on; the reminder is already marked FIRED.
                subscribers.remove(emitter);
                log.debug("Bỏ một kết nối SSE đã đóng: {}", e.getMessage());
            }
        }
    }

    /** How many clients are currently listening. Exposed for tests and diagnostics. */
    public int subscriberCount() {
        return subscribers.size();
    }
}

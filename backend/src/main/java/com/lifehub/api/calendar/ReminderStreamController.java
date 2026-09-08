package com.lifehub.api.calendar;

import com.lifehub.infrastructure.notification.ReminderSseEmitter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live push channel for fired reminders (06-API-SPEC.md §6, 04-ARCHITECTURE.md §6.2).
 *
 * <p>Kept apart from {@link EventController} on purpose. Every other calendar endpoint talks only
 * to the application layer; this one has to hand Spring MVC the emitter registry itself, and
 * isolating that in a class of its own keeps the exception visible rather than buried among
 * ordinary CRUD methods.
 *
 * <p>The path is a literal segment under {@code /events}, so it is matched ahead of
 * {@code /events/{id}} and no event id can shadow it.
 */
@RestController
public class ReminderStreamController {

    private final ReminderSseEmitter emitter;

    public ReminderStreamController(ReminderSseEmitter emitter) {
        this.emitter = emitter;
    }

    @GetMapping(path = "/api/v1/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return emitter.subscribe();
    }
}

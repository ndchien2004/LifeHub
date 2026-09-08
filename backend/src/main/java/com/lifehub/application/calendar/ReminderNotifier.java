package com.lifehub.application.calendar;

import java.util.List;

/**
 * Outbound port for reminder notifications.
 *
 * <p>Implemented by the SSE emitter in {@code infrastructure.notification}. Declared here so the
 * scheduler and the service can push notifications without knowing that the transport happens to be
 * Server-Sent Events, and so a test can substitute a recorder.
 */
public interface ReminderNotifier {

    void publish(ReminderNotification notification);

    default void publishAll(List<ReminderNotification> notifications) {
        notifications.forEach(this::publish);
    }
}

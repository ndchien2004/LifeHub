package com.lifehub.application.system;

import com.lifehub.domain.calendar.Reminder;
import java.util.List;
import java.util.Map;

/**
 * Everything the renderer needs on the first request after startup, in one round trip
 * (06-API-SPEC.md 2).
 *
 * <p>Phase 0 populated {@code settings} only; Phase 2 added {@code missedReminders} (UC-05).
 * {@code aiConfigured} arrives in Phase 4 and {@code dashboard} in Phase 3 - until then it stays
 * null and the frontend renders a placeholder.
 *
 * <p>{@code missedReminders} holds domain entities rather than DTOs: the api layer renders them
 * with the same mapper the reminder endpoints use, so the startup modal and
 * {@code GET /reminders/missed} cannot drift apart.
 */
public record BootstrapData(
        Map<String, String> settings,
        boolean aiConfigured,
        List<Reminder> missedReminders,
        Object dashboard) {
}

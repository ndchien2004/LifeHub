package com.lifehub.application.system;

import com.lifehub.domain.calendar.Reminder;
import java.util.List;
import java.util.Map;

/**
 * Everything the renderer needs on the first request after startup, in one round trip
 * (06-API-SPEC.md 2).
 *
 * <p>Phase 0 populated {@code settings} only; Phase 2 added {@code missedReminders} (UC-05) and
 * Phase 3 filled in {@code dashboard} (FR-SYS-01) and Phase 4 filled in {@code aiConfigured} - true
 * when an API key reached the backend, which is what the command palette checks before offering
 * itself as more than a rule based parser (FR-AI-09).
 *
 * <p>{@code missedReminders} holds domain entities rather than DTOs: the api layer renders them
 * with the same mapper the reminder endpoints use, so the startup modal and
 * {@code GET /reminders/missed} cannot drift apart.
 */
public record BootstrapData(
        Map<String, String> settings,
        boolean aiConfigured,
        List<Reminder> missedReminders,
        DashboardData dashboard) {
}

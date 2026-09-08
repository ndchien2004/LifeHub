package com.lifehub.application.system;

import java.util.List;
import java.util.Map;

/**
 * Everything the renderer needs on the first request after startup, in one round trip
 * (06-API-SPEC.md 2).
 *
 * <p>Phase 0 populates {@code settings} only. The remaining members exist so the response shape is
 * stable from the first release: {@code missedReminders} is filled in Phase 2 (UC-05),
 * {@code aiConfigured} in Phase 4, and {@code dashboard} in Phase 3 - until then it stays null and
 * the frontend renders a placeholder.
 */
public record BootstrapData(
        Map<String, String> settings,
        boolean aiConfigured,
        List<Object> missedReminders,
        Object dashboard) {
}

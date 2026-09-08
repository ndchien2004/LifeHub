package com.lifehub.api.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifehub.api.calendar.CalendarDtos.ReminderResponse;
import com.lifehub.api.calendar.CalendarMapper;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.application.calendar.ReminderService;
import com.lifehub.application.system.BootstrapData;
import com.lifehub.application.system.BootstrapService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single startup call that primes the renderer (06-API-SPEC.md 2). */
@RestController
@RequestMapping("/api/v1")
public class BootstrapController {

    private final BootstrapService bootstrapService;
    private final ReminderService reminderService;
    private final CalendarMapper calendarMapper;

    public BootstrapController(
            BootstrapService bootstrapService,
            ReminderService reminderService,
            CalendarMapper calendarMapper) {
        this.bootstrapService = bootstrapService;
        this.reminderService = reminderService;
        this.calendarMapper = calendarMapper;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<BootstrapResponse> bootstrap() {
        BootstrapData data = bootstrapService.load();

        List<ReminderResponse> missed = data.missedReminders().stream()
                .map(reminder -> calendarMapper.toResponse(reminder, reminderService.describe(reminder)))
                .toList();

        return ApiResponse.ok(new BootstrapResponse(
                data.settings(), data.aiConfigured(), missed, data.dashboard()));
    }

    /**
     * The startup payload as the renderer receives it.
     *
     * <p>Mirrors {@link BootstrapData} with the reminders rendered as DTOs - a JPA entity is never
     * serialised straight out of a controller (04-ARCHITECTURE.md §3, rule 4).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BootstrapResponse(
            Map<String, String> settings,
            boolean aiConfigured,
            List<ReminderResponse> missedReminders,
            Object dashboard) {
    }
}

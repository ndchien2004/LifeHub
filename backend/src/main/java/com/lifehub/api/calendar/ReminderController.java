package com.lifehub.api.calendar;

import com.lifehub.api.calendar.CalendarDtos.ReminderResponse;
import com.lifehub.api.calendar.CalendarDtos.SnoozeRequest;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.application.calendar.ReminderService;
import com.lifehub.domain.calendar.Reminder;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reminder endpoints (06-API-SPEC.md §6). */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    /** What "Nhắc lại sau" offers when the user postpones from a notification (UC-04 flow 5a). */
    private static final int DEFAULT_SNOOZE_MINUTES = 10;

    private final ReminderService reminderService;
    private final CalendarMapper mapper;

    public ReminderController(ReminderService reminderService, CalendarMapper mapper) {
        this.reminderService = reminderService;
        this.mapper = mapper;
    }

    /** Reminders that came due while the app was closed, within the last 24 hours (UC-05). */
    @GetMapping("/missed")
    public ApiResponse<List<ReminderResponse>> missed() {
        return ApiResponse.ok(toResponses(reminderService.findMissed()));
    }

    @PostMapping("/{id}/snooze")
    public ApiResponse<ReminderResponse> snooze(
            @PathVariable String id, @RequestBody(required = false) SnoozeRequest request) {

        int minutes = request == null || request.minutes() == null
                ? DEFAULT_SNOOZE_MINUTES
                : request.minutes();
        return ApiResponse.ok(toResponse(reminderService.snooze(id, minutes)));
    }

    /** Acknowledges a reminder; a reminder attached to a task also completes it (SD-03). */
    @PostMapping("/{id}/dismiss")
    public ApiResponse<ReminderResponse> dismiss(@PathVariable String id) {
        return ApiResponse.ok(toResponse(reminderService.dismiss(id)));
    }

    /** Clears the whole missed list from the startup modal in one action (UC-05 step 3). */
    @PostMapping("/dismiss-all")
    public ApiResponse<Map<String, Integer>> dismissAll() {
        return ApiResponse.ok(Map.of("dismissed", reminderService.dismissAll()));
    }

    private ReminderResponse toResponse(Reminder reminder) {
        return mapper.toResponse(reminder, reminderService.describe(reminder));
    }

    private List<ReminderResponse> toResponses(List<Reminder> reminders) {
        return reminders.stream().map(this::toResponse).toList();
    }
}

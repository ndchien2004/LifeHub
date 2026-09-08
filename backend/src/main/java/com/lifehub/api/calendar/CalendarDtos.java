package com.lifehub.api.calendar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifehub.domain.calendar.EditScope;
import com.lifehub.domain.calendar.ReminderStatus;
import com.lifehub.domain.task.Priority;
import com.lifehub.domain.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request and response shapes for the calendar module (06-API-SPEC.md §5-6).
 *
 * <p>Timestamps cross the wire as {@link OffsetDateTime} in the machine local zone; they are stored
 * as UTC instants and the mapper converts both ways.
 */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    // ---------- responses ----------

    /** What kind of thing a calendar item is, so the grid can style task deadlines differently. */
    public enum ItemKind {
        EVENT,
        TASK
    }

    /**
     * One item on the calendar grid.
     *
     * <p>{@code eventId} plus {@code occurrenceStart} identify an instance of a series: the second
     * is the slot the recurrence rule produced, which stays the anchor even after an exception
     * moved the instance elsewhere, so {@code startAt} and {@code occurrenceStart} can differ
     * (SD-04). Both are needed to edit or delete a single instance.
     *
     * <p>Task deadlines share this shape with {@code kind = TASK} and a null {@code eventId}
     * (FR-CAL-10).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalendarItemResponse(
            ItemKind kind,
            String eventId,
            OffsetDateTime occurrenceStart,
            String title,
            String description,
            String location,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean allDay,
            boolean isRecurring,
            boolean isException,
            boolean hasConflict,
            String linkedTaskId,
            List<ReminderRef> reminders,
            TaskStatus taskStatus,
            Priority taskPriority,
            Boolean isOverdue) {
    }

    public record ReminderRef(String id, int offsetMinutes) {
    }

    /** The master row behind a series, which is what the edit form loads (FR-CAL-01). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventResponse(
            String id,
            String title,
            String description,
            String location,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean allDay,
            String rrule,
            String timezone,
            String linkedTaskId,
            String linkedTaskTitle,
            List<Integer> reminderOffsets,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    /** A reminder as the missed list and the notification handlers see it (06-API-SPEC.md §6). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReminderResponse(
            String id,
            String title,
            String body,
            String refType,
            String refId,
            OffsetDateTime triggerAt,
            OffsetDateTime occurrenceStart,
            int offsetMinutes,
            ReminderStatus status,
            OffsetDateTime firedAt) {
    }

    // ---------- requests ----------

    public record CreateEventRequest(
            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
            String title,
            String description,
            @Size(max = 255, message = "Địa điểm tối đa 255 ký tự") String location,
            @NotNull(message = "Thiếu thời gian bắt đầu") OffsetDateTime startAt,
            @NotNull(message = "Thiếu thời gian kết thúc") OffsetDateTime endAt,
            boolean allDay,
            String rrule,
            String timezone,
            String taskId,
            List<Integer> reminderOffsets) {
    }

    /**
     * Body of {@code PATCH /events/{id}/occurrences/{occurrenceStart}}.
     *
     * <p>{@code scope} decides how far the edit reaches (FR-CAL-04). THIS_ONLY can only move the
     * instance and retitle it - the override table holds nothing else (03-DATA-MODEL.md §6, item
     * M-19) - so a description or location change has to be sent with scope ALL.
     */
    public record UpdateOccurrenceRequest(
            EditScope scope,
            String title,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String description,
            String location,
            String rrule,
            String timezone,
            String taskId,
            List<Integer> reminderOffsets) {
    }

    public record SnoozeRequest(Integer minutes) {
    }
}

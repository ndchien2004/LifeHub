package com.lifehub.api.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.calendar.CalendarDtos.CalendarItemResponse;
import com.lifehub.api.calendar.CalendarDtos.CreateEventRequest;
import com.lifehub.api.calendar.CalendarDtos.EventResponse;
import com.lifehub.api.calendar.CalendarDtos.UpdateOccurrenceRequest;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.application.calendar.CalendarCommands.CreateEvent;
import com.lifehub.application.calendar.CalendarCommands.UpdateEvent;
import com.lifehub.application.calendar.CalendarCommands.UpdateOccurrence;
import com.lifehub.application.calendar.EventQueryService;
import com.lifehub.application.calendar.EventQueryService.CalendarView;
import com.lifehub.application.calendar.EventService;
import com.lifehub.application.calendar.ReminderService;
import com.lifehub.domain.calendar.EditScope;
import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventOccurrence;
import com.lifehub.domain.common.Patch;
import com.lifehub.domain.common.ValidationException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Calendar endpoints (06-API-SPEC.md §5). */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final EventQueryService eventQueryService;
    private final ReminderService reminderService;
    private final CalendarMapper mapper;
    private final JsonPatchReader patches;

    public EventController(
            EventService eventService,
            EventQueryService eventQueryService,
            ReminderService reminderService,
            CalendarMapper mapper,
            JsonPatchReader patches) {
        this.eventService = eventService;
        this.eventQueryService = eventQueryService;
        this.reminderService = reminderService;
        this.mapper = mapper;
        this.patches = patches;
    }

    /**
     * Every instance between {@code from} and {@code to}, with recurrence rules already expanded
     * (SD-04).
     *
     * <p>Task deadlines are folded into the same list under {@code kind = TASK} (FR-CAL-10), so the
     * grid renders one sorted sequence rather than merging two.
     */
    @GetMapping
    public ApiResponse<List<CalendarItemResponse>> list(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = "true") boolean includeTasks) {

        CalendarView view =
                eventQueryService.findInRange(mapper.toInstant(from), mapper.toInstant(to), includeTasks);

        return ApiResponse.ok(mapper.toItems(
                view.occurrences(), view.tasksDue(), view.reminders(), view.conflictingEventIds()));
    }

    /** Instances overlapping a proposed slot, for the conflict warning in the form (FR-CAL-11). */
    @GetMapping("/conflicts")
    public ApiResponse<List<CalendarItemResponse>> conflicts(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) String excludeEventId) {

        List<EventOccurrence> clashes = eventQueryService.findConflicts(
                mapper.toInstant(from), mapper.toInstant(to), excludeEventId);

        Set<String> ids = clashes.stream().map(EventOccurrence::eventId).collect(java.util.stream.Collectors.toSet());
        return ApiResponse.ok(mapper.toItems(clashes, List.of(), List.of(), ids));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(detailOf(eventQueryService.findById(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        Event created = eventService.create(new CreateEvent(
                request.title(),
                request.description(),
                request.location(),
                mapper.toInstant(request.startAt()),
                mapper.toInstant(request.endAt()),
                request.allDay(),
                request.rrule(),
                request.timezone(),
                request.taskId(),
                request.reminderOffsets()));
        return ApiResponse.ok(detailOf(eventQueryService.findById(created.getId())));
    }

    /**
     * Updates the master row, so the change reaches every occurrence (scope ALL).
     *
     * <p>Read as a raw JSON tree because PATCH has to tell an omitted field from one explicitly
     * sent as null - clearing a location and leaving it alone are different requests.
     */
    @PatchMapping("/{id}")
    public ApiResponse<EventResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        eventService.update(id, readUpdate(body));
        return ApiResponse.ok(detailOf(eventQueryService.findById(id)));
    }

    /**
     * Edits one instance of a series at the requested scope (FR-CAL-04, SD-05).
     *
     * @return the event to refer to afterwards - a THIS_AND_FOLLOWING split answers with the newly
     *     created series, not the one that was cut
     */
    @PatchMapping("/{id}/occurrences/{occurrenceStart}")
    public ApiResponse<EventResponse> updateOccurrence(
            @PathVariable String id,
            @PathVariable String occurrenceStart,
            @RequestBody UpdateOccurrenceRequest request) {

        // startAt and endAt are carried in both places on purpose: the THIS_ONLY branch reads them
        // off the command to build an override row, while scope ALL needs them here to move the
        // master itself.
        UpdateEvent fields = new UpdateEvent(
                present(request.title()),
                present(request.description()),
                present(request.location()),
                present(mapper.toInstant(request.startAt())),
                present(mapper.toInstant(request.endAt())),
                Patch.absent(),
                present(request.rrule()),
                present(request.timezone()),
                present(request.taskId()),
                request.reminderOffsets() == null ? Patch.absent() : Patch.of(request.reminderOffsets()));

        Event result = eventService.updateOccurrence(id, new UpdateOccurrence(
                request.scope() == null ? EditScope.THIS_ONLY : request.scope(),
                parseOccurrenceStart(occurrenceStart),
                request.title(),
                mapper.toInstant(request.startAt()),
                mapper.toInstant(request.endAt()),
                fields));

        return ApiResponse.ok(detailOf(eventQueryService.findById(result.getId())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        eventService.delete(id);
        return ApiResponse.ok(null);
    }

    /** Removes a single instance by recording a cancellation, leaving the series intact (T2-14). */
    @DeleteMapping("/{id}/occurrences/{occurrenceStart}")
    public ApiResponse<Void> deleteOccurrence(
            @PathVariable String id, @PathVariable String occurrenceStart) {
        eventService.deleteOccurrence(id, parseOccurrenceStart(occurrenceStart));
        return ApiResponse.ok(null);
    }

    private EventResponse detailOf(Event event) {
        return mapper.toResponse(event, reminderService.configuredOffsets(event.getId()));
    }

    private UpdateEvent readUpdate(JsonNode body) {
        return new UpdateEvent(
                patches.read(body, "title", String.class),
                patches.read(body, "description", String.class),
                patches.read(body, "location", String.class),
                patches.readInstant(body, "startAt"),
                patches.readInstant(body, "endAt"),
                patches.read(body, "allDay", Boolean.class),
                patches.read(body, "rrule", String.class),
                patches.read(body, "timezone", String.class),
                patches.read(body, "taskId", String.class),
                patches.readList(body, "reminderOffsets", Integer.class));
    }

    private <T> Patch<T> present(T value) {
        return value == null ? Patch.absent() : Patch.of(value);
    }

    /**
     * Reads the occurrence anchor out of the path.
     *
     * <p>Parsed by hand rather than bound, so a malformed value produces the field error the form
     * can point at instead of a generic 400 with no indication of which segment was wrong.
     *
     * <p>The retry with spaces turned back into plus signs is not paranoia: the anchor is an
     * ISO-8601 timestamp whose {@code +07:00} offset is the one character that form-decoding turns
     * into a space. A client that encodes the segment as if it were a query parameter would
     * otherwise fail here with a value that looks correct in the log.
     */
    private Instant parseOccurrenceStart(String raw) {
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException first) {
            try {
                return OffsetDateTime.parse(raw.replace(' ', '+')).toInstant();
            } catch (DateTimeParseException second) {
                throw new ValidationException(
                        "Mốc thời gian của instance không hợp lệ: " + raw, "occurrenceStart");
            }
        }
    }
}

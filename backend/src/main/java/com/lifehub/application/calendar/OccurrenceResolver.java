package com.lifehub.application.calendar;

import com.lifehub.domain.calendar.Event;
import com.lifehub.domain.calendar.EventException;
import com.lifehub.domain.calendar.EventExceptionRepository;
import com.lifehub.domain.calendar.EventOccurrence;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Expands events into the instances that fall inside a window, applying per-occurrence overrides
 * (SD-04).
 *
 * <p>Shared by the calendar query side and by reminder generation, so both agree on exactly which
 * instances exist - a reminder for an occurrence the calendar has cancelled would fire a
 * notification for an event the user cannot see.
 */
@Component
public class OccurrenceResolver {

    private final RecurrenceExpander expander;
    private final EventExceptionRepository exceptionRepository;

    public OccurrenceResolver(RecurrenceExpander expander, EventExceptionRepository exceptionRepository) {
        this.expander = expander;
        this.exceptionRepository = exceptionRepository;
    }

    /** Instances of one event overlapping the half-open interval [from, to). */
    public List<EventOccurrence> resolve(Event event, Instant from, Instant to) {
        return resolve(event, exceptionRepository.findByEventId(event.getId()), from, to);
    }

    /**
     * Instances of many events, loading every override in one query.
     *
     * <p>A month view can hold dozens of repeating series; asking for each one's exceptions
     * separately is the N+1 that would break the 500 ms budget in T2-15.
     */
    public List<EventOccurrence> resolveAll(List<Event> events, Instant from, Instant to) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, List<EventException>> byEvent = new HashMap<>();
        List<String> ids = events.stream().map(Event::getId).toList();
        for (EventException override : exceptionRepository.findByEventIds(ids)) {
            byEvent.computeIfAbsent(override.getEvent().getId(), key -> new ArrayList<>()).add(override);
        }

        List<EventOccurrence> resolved = new ArrayList<>();
        for (Event event : events) {
            resolved.addAll(resolve(event, byEvent.getOrDefault(event.getId(), List.of()), from, to));
        }
        return resolved;
    }

    private List<EventOccurrence> resolve(
            Event event, List<EventException> overrides, Instant from, Instant to) {

        Map<Instant, EventException> byOriginalStart = new HashMap<>();
        for (EventException override : overrides) {
            byOriginalStart.put(override.getOriginalStartAt(), override);
        }

        // The rule decides which slots exist inside the window. The lower bound is pulled back by
        // the event's length so an instance that started before the window but is still running
        // inside it - a three hour meeting on a day view that opens at noon - is not lost.
        Set<Instant> slots;
        if (event.isRecurring()) {
            Instant searchFrom = from.minus(event.duration());
            slots = new LinkedHashSet<>(
                    expander.expand(event.getRrule(), event.getStartAt(), event.zone(), searchFrom, to));
        } else {
            slots = new LinkedHashSet<>(List.of(event.getStartAt()));
        }

        // ...but a moved instance may have been dragged in from outside it, and the rule alone
        // would never produce that slot. Its override row is the only record that it belongs here.
        for (EventException override : overrides) {
            if (override.getNewStartAt() != null && !slots.contains(override.getOriginalStartAt())) {
                slots.add(override.getOriginalStartAt());
            }
        }

        List<EventOccurrence> occurrences = new ArrayList<>(slots.size());
        for (Instant slot : slots) {
            EventException override = byOriginalStart.get(slot);
            if (override != null && override.isCancelled()) {
                continue;
            }
            EventOccurrence occurrence = EventOccurrence.of(event, slot);
            if (override != null) {
                occurrence = occurrence.withOverrides(override);
            }
            // Re-checked after the override, so an instance moved out of the window disappears
            // from it and one moved in shows up.
            if (occurrence.overlaps(from, to)) {
                occurrences.add(occurrence);
            }
        }
        return occurrences;
    }
}

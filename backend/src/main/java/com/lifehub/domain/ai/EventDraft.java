package com.lifehub.domain.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * An event the user probably meant (FR-AI-05), still unsaved.
 *
 * <p>{@code reminderOffsetMinutes} is filtered to the six values FR-CAL-06 allows before it reaches
 * here, so the prefilled event form never shows a reminder interval its own picker cannot express.
 */
public record EventDraft(
        String title,
        Instant startAt,
        Instant endAt,
        String location,
        List<Integer> reminderOffsetMinutes,
        Map<String, Double> fieldConfidence) {

    public EventDraft {
        reminderOffsetMinutes =
                reminderOffsetMinutes == null ? List.of() : List.copyOf(reminderOffsetMinutes);
        fieldConfidence = fieldConfidence == null ? Map.of() : Map.copyOf(fieldConfidence);
    }
}

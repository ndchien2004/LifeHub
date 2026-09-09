package com.lifehub.domain.ai;

import com.lifehub.domain.task.Priority;
import java.time.Instant;
import java.util.Map;

/** A task the user probably meant (FR-AI-04), still unsaved. */
public record TaskDraft(
        String title,
        Priority priority,
        Instant dueAt,
        String projectId,
        String projectName,
        Map<String, Double> fieldConfidence) {

    public TaskDraft {
        fieldConfidence = fieldConfidence == null ? Map.of() : Map.copyOf(fieldConfidence);
    }
}

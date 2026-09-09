package com.lifehub.api.ai;

import com.lifehub.api.ai.AiDtos.AiLogResponse;
import com.lifehub.api.ai.AiDtos.AiStatusResponse;
import com.lifehub.api.ai.AiDtos.CategorySuggestionResponse;
import com.lifehub.api.ai.AiDtos.EventDraftResponse;
import com.lifehub.api.ai.AiDtos.ParseResponse;
import com.lifehub.api.ai.AiDtos.TaskDraftResponse;
import com.lifehub.api.ai.AiDtos.TransactionDraftResponse;
import com.lifehub.application.ai.AiStatusService.AiStatus;
import com.lifehub.domain.ai.AiParseLog;
import com.lifehub.domain.ai.CategorySuggestion;
import com.lifehub.domain.ai.EventDraft;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.TaskDraft;
import com.lifehub.domain.ai.TransactionDraft;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Converts AI domain objects into API responses.
 *
 * <p>Instants become {@link OffsetDateTime} in the display zone, matching every other module: the
 * renderer drops these values straight into a datetime input, and a bare UTC {@code Z} would put a
 * two o'clock meeting at nine in the morning.
 */
@Component
public class AiMapper {

    private final ZoneId displayZone;

    public AiMapper(ZoneId displayZone) {
        this.displayZone = displayZone;
    }

    public ParseResponse toResponse(ParseResult result) {
        return new ParseResponse(
                result.intent(),
                result.confidence(),
                result.source(),
                toResponse(result.transaction()),
                toResponse(result.task()),
                toResponse(result.event()),
                result.warning());
    }

    private TransactionDraftResponse toResponse(TransactionDraft draft) {
        if (draft == null) {
            return null;
        }
        return new TransactionDraftResponse(
                draft.type(),
                draft.amount(),
                draft.categoryId(),
                draft.categoryName(),
                draft.walletId(),
                draft.walletName(),
                draft.note(),
                toOffset(draft.occurredAt()),
                draft.fieldConfidence());
    }

    private TaskDraftResponse toResponse(TaskDraft draft) {
        if (draft == null) {
            return null;
        }
        return new TaskDraftResponse(
                draft.title(),
                draft.priority(),
                toOffset(draft.dueAt()),
                draft.projectId(),
                draft.projectName(),
                draft.fieldConfidence());
    }

    private EventDraftResponse toResponse(EventDraft draft) {
        if (draft == null) {
            return null;
        }
        return new EventDraftResponse(
                draft.title(),
                toOffset(draft.startAt()),
                toOffset(draft.endAt()),
                draft.location(),
                draft.reminderOffsetMinutes(),
                draft.fieldConfidence());
    }

    public CategorySuggestionResponse toResponse(CategorySuggestion suggestion) {
        return new CategorySuggestionResponse(
                suggestion.categoryId(), suggestion.categoryName(), suggestion.confidence());
    }

    public AiStatusResponse toResponse(AiStatus status) {
        return new AiStatusResponse(
                status.enabled(), status.configured(), status.model(), status.available());
    }

    public AiLogResponse toResponse(AiParseLog entry) {
        return new AiLogResponse(
                entry.getId(),
                entry.getRequestType().name(),
                entry.getInputText(),
                entry.getOutputJson(),
                entry.getIntent(),
                entry.isSuccess(),
                entry.getErrorCode(),
                entry.getLatencyMs(),
                entry.getTokenInput(),
                entry.getTokenOutput(),
                entry.getModel(),
                toOffset(entry.getCreatedAt()));
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(displayZone).toOffsetDateTime();
    }
}

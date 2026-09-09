package com.lifehub.application.ai;

import com.lifehub.domain.ai.AiClient;
import com.lifehub.domain.ai.AiClient.AiCompletion;
import com.lifehub.domain.ai.AiClient.AiRequest;
import com.lifehub.domain.ai.AiErrorCode;
import com.lifehub.domain.ai.AiException;
import com.lifehub.domain.ai.AiInvalidResponseException;
import com.lifehub.domain.ai.AiRequestType;
import com.lifehub.domain.ai.AiResponseReader;
import com.lifehub.domain.ai.FallbackParser;
import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseResult;
import com.lifehub.domain.ai.PromptTemplates;
import com.lifehub.domain.ai.PromptTemplates.Prompt;
import com.lifehub.domain.common.ValidationException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a sentence the user typed into a draft they can review (SD-02, UC-09).
 *
 * <p><strong>This class never writes business data, and there is no path from it that can.</strong>
 * It holds no transaction, task, event or wallet service, and the only repository it can reach is
 * the AI call log. Creating a record happens when the user confirms the prefilled form and the
 * frontend issues its own POST - AGENTS.md 3.4 rule 1, enforced by
 * {@code NlParseServiceIsolationTest}.
 *
 * <p>The order of the branches is the order of SD-02: AI off or unconfigured goes straight to the
 * rules; a malformed answer is retried once with a stricter prompt; anything still broken, and any
 * transport failure, falls back. Every one of those paths writes exactly one log row.
 */
@Service
public class NlParseService {

    private static final Logger log = LoggerFactory.getLogger(NlParseService.class);

    private static final String TEMPLATE = "nl-parse";
    private static final int MAX_INPUT_LENGTH = 500;

    /**
     * Appended to the user prompt on the single retry SD-02 allows.
     *
     * <p>Lives here rather than in the template because it is not part of the task description - it
     * is what to say to a model that has just ignored the format instruction it was already given.
     */
    private static final String RETRY_HINT =
            "LƯU Ý: Lần trước bạn trả về sai định dạng. CHỈ trả về JSON thuần theo schema, "
                    + "không thêm bất kỳ ký tự nào khác.";

    private final AiSettings settings;
    private final AiContextProvider contextProvider;
    private final PromptTemplates prompts;
    private final AiClient aiClient;
    private final AiResponseReader reader;
    private final FallbackParser fallbackParser;
    private final AiLogService logService;

    public NlParseService(
            AiSettings settings,
            AiContextProvider contextProvider,
            PromptTemplates prompts,
            AiClient aiClient,
            AiResponseReader reader,
            FallbackParser fallbackParser,
            AiLogService logService) {
        this.settings = settings;
        this.contextProvider = contextProvider;
        this.prompts = prompts;
        this.aiClient = aiClient;
        this.reader = reader;
        this.fallbackParser = fallbackParser;
        this.logService = logService;
    }

    /**
     * Reads {@code text} and returns what the user probably meant.
     *
     * <p>Deliberately not {@code @Transactional}: the AI call can take seconds, and holding a
     * database transaction open across it would pin a SQLite connection for the whole round trip.
     * The context load and the log write each open their own.
     */
    public ParseResult parse(String text) {
        String input = validated(text);
        ParseContext context = contextProvider.load();

        if (!settings.isUsable()) {
            // Not an error: the user switched AI off, or has not supplied a key yet.
            logService.recordFailure(AiRequestType.NL_PARSE, input, null, null, 0L);
            return fallback(input, context, null);
        }

        String model = settings.model();
        long startedAt = System.nanoTime();

        try {
            return attempt(input, context, model, false, startedAt);
        } catch (AiInvalidResponseException first) {
            log.debug("AI trả sai schema, thử lại một lần", first);
            try {
                return attempt(input, context, model, true, startedAt);
            } catch (AiException retry) {
                return fallbackAndLog(input, context, model, retry.getErrorCode(), startedAt);
            }
        } catch (AiException transport) {
            return fallbackAndLog(input, context, model, transport.getErrorCode(), startedAt);
        }
    }

    /** One round trip: build the prompt, call, sanitise, validate, log. */
    private ParseResult attempt(
            String input, ParseContext context, String model, boolean retry, long startedAt) {

        Prompt prompt = prompts.build(
                TEMPLATE, AiPromptVariables.forParse(input, context, retry ? RETRY_HINT : ""));

        // Null timeout and zero max tokens mean "use the client's configured defaults", which keeps
        // provider tuning in infrastructure where it belongs.
        AiCompletion completion = aiClient.complete(
                new AiRequest(model, prompt.system(), prompt.user(), null, 0));

        ParseResult result = reader.readParse(completion.text(), context);

        logService.recordSuccess(
                AiRequestType.NL_PARSE,
                input,
                result.intent(),
                completion.text(),
                completion.model(),
                elapsedMs(startedAt),
                completion.inputTokens(),
                completion.outputTokens());

        return result;
    }

    private ParseResult fallbackAndLog(
            String input, ParseContext context, String model, AiErrorCode code, long startedAt) {
        logService.recordFailure(AiRequestType.NL_PARSE, input, code, model, elapsedMs(startedAt));
        return fallback(input, context, code);
    }

    private ParseResult fallback(String input, ParseContext context, AiErrorCode code) {
        ParseResult result = fallbackParser.parse(input, context);
        return code == null ? result : result.withWarning(code);
    }

    private String validated(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Hãy nhập nội dung cần phân tích", "text");
        }
        if (trimmed.length() > MAX_INPUT_LENGTH) {
            throw new ValidationException(
                    "Nội dung tối đa " + MAX_INPUT_LENGTH + " ký tự", "text");
        }
        return trimmed;
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}

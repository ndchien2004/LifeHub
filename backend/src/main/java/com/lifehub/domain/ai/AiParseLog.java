package com.lifehub.domain.ai;

import com.lifehub.domain.common.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per AI call, successful or not (FR-AI-10, 03-DATA-MODEL.md 2.10).
 *
 * <p>Does not extend {@code BaseEntity}: a log row is written once and never updated, so an
 * {@code updated_at} column would only ever repeat {@code created_at}. The schema reflects that.
 *
 * <p>Rows older than 90 days are deleted at startup. What is stored is the user's own sentence and
 * the model's answer - never the API key, which the backend receives from the environment and never
 * writes anywhere (NFR-SEC-01).
 */
@Entity
@Table(name = "ai_parse_log")
public class AiParseLog {

    /** Long sentences are truncated: the log exists to explain a result, not to archive prose. */
    public static final int MAX_TEXT_LENGTH = 2_000;

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private AiRequestType requestType;

    @Column(name = "input_text")
    private String inputText;

    @Column(name = "output_json")
    private String outputJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent")
    private ParseIntent intent;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private AiErrorCode errorCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "token_input")
    private Integer tokenInput;

    @Column(name = "token_output")
    private Integer tokenOutput;

    @Column(name = "model")
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiParseLog() {
    }

    private AiParseLog(AiRequestType requestType, String inputText, Instant createdAt) {
        this.id = IdGenerator.newId();
        this.requestType = requestType;
        this.inputText = truncate(inputText);
        this.createdAt = createdAt;
    }

    /** Starts a row for a call that is about to be made. */
    public static AiParseLog started(AiRequestType requestType, String inputText, Instant now) {
        return new AiParseLog(requestType, inputText, now);
    }

    /** Records a call that returned usable output. */
    public AiParseLog succeeded(
            ParseIntent intent, String outputJson, String model, long latencyMs, int in, int out) {
        this.success = true;
        this.errorCode = null;
        this.intent = intent;
        this.outputJson = truncate(outputJson);
        this.model = model;
        this.latencyMs = latencyMs;
        this.tokenInput = in;
        this.tokenOutput = out;
        return this;
    }

    /** Records a call that did not produce usable output, and why. */
    public AiParseLog failed(AiErrorCode errorCode, String model, long latencyMs) {
        this.success = false;
        this.errorCode = errorCode == null ? AiErrorCode.UNKNOWN : errorCode;
        this.model = model;
        this.latencyMs = latencyMs;
        return this;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    public String getId() {
        return id;
    }

    public AiRequestType getRequestType() {
        return requestType;
    }

    public String getInputText() {
        return inputText;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public ParseIntent getIntent() {
        return intent;
    }

    public boolean isSuccess() {
        return success;
    }

    public AiErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Integer getTokenInput() {
        return tokenInput;
    }

    public Integer getTokenOutput() {
        return tokenOutput;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.lifehub.infrastructure.ai;

import com.lifehub.domain.ai.AiInvalidResponseException;
import org.springframework.stereotype.Component;

/**
 * Turns whatever the model actually sent into something Jackson can read (step 4 of
 * 04-ARCHITECTURE.md 7).
 *
 * <p>Models asked for "JSON only" still wrap the answer in a ```json fence, prefix it with "Sure,
 * here you go", or emit a byte order mark. None of that is an error worth failing a user's input
 * over, so all of it is stripped here. What is left must be a single balanced JSON value; if it is
 * not, that is a real failure and the caller retries or falls back.
 *
 * <p>The brace scan tracks string literals and escapes, so a closing brace inside a note - "cơm gà
 * {ăn cùng team}" - cannot end the object early.
 */
@Component
public class JsonSanitizer {

    private static final char BOM = '\uFEFF';

    /**
     * Extracts the JSON value embedded in {@code raw}.
     *
     * @throws AiInvalidResponseException when there is no balanced JSON object or array in the text
     */
    public String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiInvalidResponseException("AI trả về nội dung rỗng");
        }

        String text = stripControlCharacters(raw);
        text = stripCodeFence(text).trim();

        int start = firstOpeningBrace(text);
        if (start < 0) {
            throw new AiInvalidResponseException("AI trả về nội dung không phải JSON");
        }

        int end = matchingCloseIndex(text, start);
        if (end < 0) {
            throw new AiInvalidResponseException("AI trả về JSON không đóng đủ dấu ngoặc");
        }

        return text.substring(start, end + 1);
    }

    /**
     * Removes the byte order mark and C0 control characters.
     *
     * <p>Tab, newline and carriage return survive: they are legal JSON whitespace between tokens.
     * Anything else below 0x20 is illegal inside a JSON string and would fail the parse for a
     * reason the user can do nothing about.
     */
    private String stripControlCharacters(String raw) {
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == BOM) {
                continue;
            }
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                continue;
            }
            cleaned.append(c);
        }
        return cleaned.toString();
    }

    /** Unwraps a fenced block, tolerating both ```json and a bare ```. */
    private String stripCodeFence(String text) {
        int open = text.indexOf("```");
        if (open < 0) {
            return text;
        }

        int lineEnd = text.indexOf('\n', open);
        if (lineEnd < 0) {
            // A fence with nothing after it on any line: no content to unwrap.
            return text.substring(0, open);
        }

        int close = text.indexOf("```", lineEnd);
        return close < 0 ? text.substring(lineEnd + 1) : text.substring(lineEnd + 1, close);
    }

    private int firstOpeningBrace(String text) {
        int object = text.indexOf('{');
        int array = text.indexOf('[');
        if (object < 0) {
            return array;
        }
        if (array < 0) {
            return object;
        }
        return Math.min(object, array);
    }

    /** Index of the brace closing the one at {@code start}, or -1 if the text runs out first. */
    private int matchingCloseIndex(String text, int start) {
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}

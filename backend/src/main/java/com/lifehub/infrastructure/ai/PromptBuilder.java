package com.lifehub.infrastructure.ai;

import com.lifehub.domain.ai.PromptTemplates;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads a prompt template from {@code resources/prompts/} and fills in its placeholders.
 *
 * <p>Templates are cached after the first read: they are packaged inside the jar and cannot change
 * while the process runs, and natural language input should not pay for a classpath lookup.
 *
 * <p>Substitution is done with {@link Matcher#quoteReplacement} so a value containing a backslash or
 * a dollar sign - entirely possible in a user's own note or category name - is inserted literally
 * rather than interpreted as a group reference.
 */
@Component
public class PromptBuilder implements PromptTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    /** Everything before this line is the system message, everything after it the user message. */
    private static final String USER_MARKER = "=== USER ===";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public Prompt build(String name, Map<String, String> variables) {
        String filled = substitute(cache.computeIfAbsent(name, PromptBuilder::load), variables);

        int marker = filled.indexOf(USER_MARKER);
        if (marker < 0) {
            return new Prompt("", filled.trim());
        }
        return new Prompt(
                filled.substring(0, marker).trim(),
                filled.substring(marker + USER_MARKER.length()).trim());
    }

    private String substitute(String template, Map<String, String> variables) {
        Map<String, String> values = variables == null ? Map.of() : variables;

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // A missing template is a packaging fault, not a runtime condition to recover from.
            throw new UncheckedIOException("Không đọc được prompt template: " + name, e);
        }
    }
}

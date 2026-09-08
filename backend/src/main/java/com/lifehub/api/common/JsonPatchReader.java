package com.lifehub.api.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.domain.common.Patch;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads PATCH bodies as a raw JSON tree.
 *
 * <p>Binding a PATCH body to a record loses the one distinction that matters: a field the caller
 * omitted and a field the caller explicitly set to null both arrive as null. Reading the tree keeps
 * them apart, and every controller that supports partial updates needs the same four readers.
 */
@Component
public class JsonPatchReader {

    private final ObjectMapper objectMapper;

    public JsonPatchReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> Patch<T> read(JsonNode body, String field, Class<T> type) {
        if (body == null || !body.has(field)) {
            return Patch.absent();
        }
        JsonNode node = body.get(field);
        return node.isNull() ? Patch.of(null) : Patch.of(objectMapper.convertValue(node, type));
    }

    /** An ISO-8601 timestamp with offset, converted to the UTC instant that gets stored. */
    public Patch<Instant> readInstant(JsonNode body, String field) {
        Patch<OffsetDateTime> raw = read(body, field, OffsetDateTime.class);
        if (!raw.present()) {
            return Patch.absent();
        }
        return Patch.of(raw.value() == null ? null : raw.value().toInstant());
    }

    /** A JSON array; an explicit null is read as an empty list, which clears the collection. */
    public <T> Patch<List<T>> readList(JsonNode body, String field, Class<T> elementType) {
        if (body == null || !body.has(field)) {
            return Patch.absent();
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return Patch.of(List.of());
        }
        return Patch.of(objectMapper.convertValue(
                node, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)));
    }
}

package com.lifehub.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Stores a {@link LocalDate} as {@code yyyy-MM-dd} text (03-DATA-MODEL.md 6, item M-21).
 *
 * <p>SQLite has no date type, and the driver's default handling of temporal values writes epoch
 * milliseconds - a representation that is meaningless for a value with no time and no zone, and
 * that no human can read when inspecting the file. ISO text sorts correctly as a string, so range
 * queries on {@code start_date} and {@code next_run_date} still work in SQL.
 */
@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.format(FORMAT);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : LocalDate.parse(dbData, FORMAT);
    }
}

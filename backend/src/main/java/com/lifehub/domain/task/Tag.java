package com.lifehub.domain.task;

import com.lifehub.domain.common.IdGenerator;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A colour coded label attachable to tasks and, from Phase 3, to transactions (FR-PRJ-04).
 *
 * <p>Does not extend {@code BaseEntity}: the locked schema gives {@code tag} a {@code created_at}
 * but no {@code updated_at}, and inventing the column to fit a superclass would change a table
 * definition nobody approved.
 *
 * <p>Tags are also the one deliberate exception to the soft delete rule (03-DATA-MODEL.md §6,
 * item C-5b) — there is no {@code deleted_at}, and deleting a tag detaches it from everything
 * through the join table's cascade.
 */
@Entity
@Table(name = "tag")
@org.hibernate.annotations.BatchSize(size = 200)
public class Tag {

    public static final int MAX_NAME_LENGTH = 50;
    private static final String DEFAULT_COLOR = "#94a3b8";

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color", nullable = false)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
    }

    public Tag(String name, String color) {
        this.id = IdGenerator.newId();
        this.createdAt = Instant.now();
        rename(name);
        recolor(color);
    }

    public void rename(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tên nhãn không được để trống", "name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Tên nhãn tối đa " + MAX_NAME_LENGTH + " ký tự", "name");
        }
        this.name = trimmed;
    }

    public void recolor(String color) {
        this.color = color == null || color.isBlank() ? DEFAULT_COLOR : color.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Tag tag && Objects.equals(id, tag.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

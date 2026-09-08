package com.lifehub.domain.task;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** A named group of related tasks (FR-PRJ-01). */
@Entity
@Table(name = "project")
@org.hibernate.annotations.BatchSize(size = 200)
public class Project extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 100;
    private static final String DEFAULT_COLOR = "#6366f1";

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color", nullable = false)
    private String color;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Project() {
    }

    public Project(String name, String color, String description, ProjectStatus status) {
        rename(name);
        recolor(color);
        this.description = description;
        this.status = status == null ? ProjectStatus.ACTIVE : status;
    }

    public void rename(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tên dự án không được để trống", "name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Tên dự án tối đa " + MAX_NAME_LENGTH + " ký tự", "name");
        }
        this.name = trimmed;
    }

    public void recolor(String color) {
        this.color = color == null || color.isBlank() ? DEFAULT_COLOR : color.trim();
    }

    public void describe(String description) {
        this.description = description;
    }

    public void changeStatus(ProjectStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

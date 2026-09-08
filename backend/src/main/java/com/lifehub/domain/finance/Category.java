package com.lifehub.domain.finance;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A spending or income classification, arranged in at most two levels (FR-FIN-02).
 *
 * <p>The depth limit is enforced here rather than in the database: SQLite cannot express "a parent
 * must not itself have a parent" as a CHECK. Two levels is a product decision - deeper trees make
 * the pie chart unreadable and the picker unusable.
 *
 * <p>System categories (FR-FIN-03) are seeded by {@code V5__seed_categories.sql}. They can be
 * renamed and recoloured but never deleted, so a chart from last year keeps its labels.
 */
@Entity
@Table(name = "category")
@org.hibernate.annotations.BatchSize(size = 200)
public class Category extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 100;
    private static final String DEFAULT_COLOR = "#94a3b8";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CategoryType type = CategoryType.EXPENSE;

    @Column(name = "icon")
    private String icon;

    @Column(name = "color", nullable = false)
    private String color = DEFAULT_COLOR;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Category() {
    }

    public Category(String name, CategoryType type, Category parent, String icon, String color) {
        rename(name);
        this.type = type == null ? CategoryType.EXPENSE : type;
        attachTo(parent);
        this.icon = icon;
        recolor(color);
    }

    public void rename(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tên danh mục không được để trống", "name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Tên danh mục tối đa " + MAX_NAME_LENGTH + " ký tự", "name");
        }
        this.name = trimmed;
    }

    /**
     * Moves this category under {@code parent}, or to the root when {@code parent} is null.
     *
     * <p>Rejects the three ways a two level tree can be broken: a category owning itself, a child
     * of a child, and a parent whose type differs from the child's.
     */
    public void attachTo(Category parent) {
        if (parent == null) {
            this.parent = null;
            return;
        }
        if (parent.getId().equals(getId())) {
            throw new ValidationException("Danh mục không thể là cha của chính nó", "parentId");
        }
        if (parent.getParent() != null) {
            throw new ValidationException("Danh mục chỉ hỗ trợ 2 cấp", "parentId");
        }
        if (parent.getType() != type) {
            throw new ValidationException(
                    "Danh mục con phải cùng loại thu/chi với danh mục cha", "parentId");
        }
        this.parent = parent;
    }

    public void recolor(String color) {
        this.color = color == null || color.isBlank() ? DEFAULT_COLOR : color.trim();
    }

    public void changeIcon(String icon) {
        this.icon = icon;
    }

    public void changeSortOrder(Integer sortOrder) {
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    /** Only used by tests and the seed reader; the type of a saved category never changes. */
    public void markSystem(boolean system) {
        this.system = system;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public Category getParent() {
        return parent;
    }

    public String getParentId() {
        return parent == null ? null : parent.getId();
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public boolean isSystem() {
        return system;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

package com.lifehub.domain.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single application preference, stored as a key/value pair (03-DATA-MODEL.md 2.11).
 *
 * <p>This table deliberately does not extend {@link com.lifehub.domain.common.BaseEntity}: the key
 * itself is the primary key, and settings carry no creation timestamp.
 *
 * <p>The AI API key is never stored here. It lives in the Electron {@code safeStorage} keychain and
 * reaches the backend through an environment variable at spawn time.
 */
@Entity
@Table(name = "setting")
public class Setting {

    @Id
    @Column(name = "\"key\"", nullable = false)
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

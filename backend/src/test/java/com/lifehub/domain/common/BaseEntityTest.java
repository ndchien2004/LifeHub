package com.lifehub.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Identity and audit behaviour every entity inherits. */
class BaseEntityTest {

    /** Minimal concrete entity; BaseEntity is abstract and carries no state of its own. */
    private static final class SampleEntity extends BaseEntity {
    }

    @Test
    @DisplayName("Entity có id UUID v7 ngay khi khởi tạo, chưa cần lưu xuống DB")
    void assignsIdentityOnConstruction() {
        SampleEntity entity = new SampleEntity();

        assertThat(entity.getId()).isNotBlank();
        assertThat(UUID.fromString(entity.getId()).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Hai entity khác nhau không bao giờ trùng id")
    void assignsADistinctIdentityPerInstance() {
        assertThat(new SampleEntity().getId()).isNotEqualTo(new SampleEntity().getId());
    }

    @Test
    @DisplayName("So sánh entity dựa trên id, không dựa trên tham chiếu")
    void comparesByIdentity() {
        SampleEntity entity = new SampleEntity();
        SampleEntity other = new SampleEntity();

        assertThat(entity).isEqualTo(entity).hasSameHashCodeAs(entity);
        assertThat(entity).isNotEqualTo(other);
        assertThat(entity).isNotEqualTo(null).isNotEqualTo("not an entity");
    }

    @Test
    @DisplayName("Lưu lần đầu ghi cả created_at và updated_at")
    void stampsBothTimestampsOnFirstPersist() {
        SampleEntity entity = new SampleEntity();
        assertThat(entity.getCreatedAt()).isNull();

        entity.onPersist();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(entity.getCreatedAt());
    }

    @Test
    @DisplayName("Cập nhật chỉ đổi updated_at, created_at giữ nguyên")
    void keepsCreationTimestampStableAcrossUpdates() throws InterruptedException {
        SampleEntity entity = new SampleEntity();
        entity.onPersist();
        Instant createdAt = entity.getCreatedAt();

        Thread.sleep(5);
        entity.onUpdate();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfter(createdAt);
    }
}

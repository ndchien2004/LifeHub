package com.lifehub.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T0-07 — identifiers are unique and ordered by creation time. */
class IdGeneratorTest {

    private static final int SAMPLE_SIZE = 10_000;

    @Test
    @DisplayName("T0-07 — 10.000 lần sinh không trùng nhau")
    void generatesUniqueIds() {
        Set<String> ids = new HashSet<>(SAMPLE_SIZE);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            assertThat(ids.add(IdGenerator.newId())).as("id thứ %d bị trùng", i).isTrue();
        }
        assertThat(ids).hasSize(SAMPLE_SIZE);
    }

    @Test
    @DisplayName("T0-07 — id sinh sau mốc thời gian mới luôn lớn hơn id sinh trước đó")
    void idsAreOrderedByTime() throws InterruptedException {
        List<String> earlier = generateBatch(500);
        // UUID v7 has millisecond resolution: ordering is only defined across millisecond boundaries,
        // the trailing bits inside one millisecond are random.
        Thread.sleep(5);
        List<String> later = generateBatch(500);

        String lastOfEarlier = earlier.stream().max(String::compareTo).orElseThrow();
        String firstOfLater = later.stream().min(String::compareTo).orElseThrow();

        assertThat(firstOfLater).isGreaterThan(lastOfEarlier);
    }

    @Test
    @DisplayName("Định dạng đúng chuẩn UUID version 7")
    void producesVersion7Uuids() {
        UUID parsed = UUID.fromString(IdGenerator.newId());

        assertThat(parsed.version()).isEqualTo(7);
        assertThat(parsed.variant()).isEqualTo(2);
    }

    private List<String> generateBatch(int size) {
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(IdGenerator.newId());
        }
        return ids;
    }
}

package com.lifehub.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T3-01, T3-02 — the value object that keeps floating point out of the ledger. */
class MoneyTest {

    @ParameterizedTest
    @ValueSource(longs = {-1L, -1000L, Long.MIN_VALUE})
    @DisplayName("T3-01 — Money từ số âm ném IllegalArgumentException")
    void rejectsNegativeAmounts(long value) {
        assertThatThrownBy(() -> Money.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được âm");
    }

    /**
     * The guarantee this whole class exists for.
     *
     * <p>Checked by reflection rather than by "we did not write one", because the danger is a
     * future convenience overload added in good faith. A single {@code Money.of(45.5)} anywhere
     * would silently reintroduce rounding into a ledger, and no unit test of arithmetic would
     * catch it.
     */
    @Test
    @DisplayName("T3-02 — Money không có bất kỳ cách khởi tạo nào nhận double hoặc float")
    void hasNoFloatingPointEntryPoint() {
        for (Constructor<?> constructor : Money.class.getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes())
                    .as("constructor %s không được nhận số thực", constructor)
                    .doesNotContain(double.class, float.class, Double.class, Float.class);
        }

        Method[] factories = Arrays.stream(Money.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        for (Method factory : factories) {
            assertThat(factory.getParameterTypes())
                    .as("factory %s không được nhận số thực", factory.getName())
                    .doesNotContain(double.class, float.class, Double.class, Float.class);
        }
    }

    @Test
    @DisplayName("Cộng 1.000 số tiền lẻ vẫn khớp tuyệt đối, không sai một đồng")
    void addsExactlyOverManyOperations() {
        Money total = Money.ZERO;
        long expected = 0L;

        for (int i = 1; i <= 1_000; i++) {
            long amount = 33_333L + i * 7L;
            total = total.plus(Money.of(amount));
            expected += amount;
        }

        assertThat(total.toLong()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Trừ không bao giờ cho ra số âm")
    void refusesToGoNegative() {
        assertThatThrownBy(() -> Money.of(1_000L).minus(Money.of(1_001L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tỉ lệ so với hạn mức 0 coi như đã dùng hết, không chia cho 0")
    void treatsZeroLimitAsFullyUsed() {
        assertThat(Money.of(1_000L).ratioTo(Money.ZERO)).isEqualTo(1.0);
        assertThat(Money.ZERO.ratioTo(Money.ZERO)).isEqualTo(0.0);
        assertThat(Money.of(2_400_000L).ratioTo(Money.of(3_000_000L))).isEqualTo(0.8);
    }

    @Test
    @DisplayName("Tràn số bị phát hiện thay vì âm thầm quay vòng")
    void detectsOverflow() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE).plus(Money.of(1L)))
                .isInstanceOf(ArithmeticException.class);
    }
}

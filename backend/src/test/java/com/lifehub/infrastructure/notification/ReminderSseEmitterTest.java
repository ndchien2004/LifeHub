package com.lifehub.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.lifehub.application.calendar.ReminderNotification;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Subscriber bookkeeping for the reminder push channel (SD-03).
 *
 * <p>Scoped to the registry rather than the wire format on purpose: what can actually go wrong here
 * is a connection that is never removed, or one dead client aborting delivery to the others. The
 * bytes themselves are covered by {@code ReminderApiIT} and by the Phase 2 UAT.
 */
class ReminderSseEmitterTest {

    private static final ReminderNotification NOTIFICATION =
            new ReminderNotification("r1", "Họp review sprint", "14:00 · Phòng họp A", "EVENT", "e1");

    @Test
    @DisplayName("Mỗi lần đăng ký thêm đúng một client vào danh sách")
    void tracksItsSubscribers() {
        ReminderSseEmitter registry = new ReminderSseEmitter();
        assertThat(registry.subscriberCount()).isZero();

        registry.subscribe();
        registry.subscribe();

        assertThat(registry.subscriberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Client đã kết thúc bị gỡ ở lượt đẩy kế tiếp, danh sách không phình theo reconnect")
    void aCompletedSubscriberIsRemovedOnTheNextPush() {
        ReminderSseEmitter registry = new ReminderSseEmitter();
        SseEmitter closed = registry.subscribe();
        registry.subscribe();
        closed.complete();

        registry.publish(NOTIFICATION);

        // Spring also calls the onCompletion callback once the async request really finishes, but
        // that needs a servlet container; the publish-time sweep is the half that works everywhere
        // and is what stops a leak if a callback is ever missed.
        assertThat(registry.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Client đã ngắt kết nối bị loại bỏ thay vì làm hỏng lượt đẩy")
    void aDeadSubscriberIsDroppedRatherThanBreakingThePush() {
        ReminderSseEmitter registry = new ReminderSseEmitter();
        SseEmitter emitter = registry.subscribe();
        emitter.completeWithError(new IOException("client đã đóng cửa sổ"));

        assertThatCode(() -> registry.publish(NOTIFICATION)).doesNotThrowAnyException();

        assertThat(registry.subscriberCount())
                .as("reminder đã được đánh dấu FIRED, lượt đẩy không được thất bại vì client biến mất")
                .isZero();
    }

    @Test
    @DisplayName("Không có ai lắng nghe thì đẩy thông báo vẫn an toàn")
    void publishingWithNoSubscribersIsHarmless() {
        ReminderSseEmitter registry = new ReminderSseEmitter();

        assertThatCode(() -> registry.publishAll(List.of(NOTIFICATION, NOTIFICATION)))
                .doesNotThrowAnyException();

        assertThat(registry.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("Đẩy nhiều thông báo một lượt gọi publish cho từng cái")
    void publishAllForwardsEachNotification() {
        List<ReminderNotification> received = new java.util.ArrayList<>();
        ReminderSseEmitter registry = new ReminderSseEmitter() {
            @Override
            public void publish(ReminderNotification notification) {
                received.add(notification);
            }
        };

        registry.publishAll(List.of(NOTIFICATION, NOTIFICATION));

        assertThat(received).hasSize(2);
    }
}

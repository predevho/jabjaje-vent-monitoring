package vent.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게이트웨이 로컬 버퍼 단위 테스트.
 *
 * <p>테스트 지점: 버퍼의 공개 메서드. 연결이 끊긴 동안 쌓고, 넘치면 가장 오래된 것부터 버리고, 오래된 순서로 꺼내는지 본다(스펙 9.1 C11).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
class TelemetryBufferTest {

    @Test
    @DisplayName("넣은 순서대로, 오래된 것부터 꺼낸다")
    void drainsOldestFirst() {
        TelemetryBuffer<String> buffer = new TelemetryBuffer<>(3);
        buffer.add("a");
        buffer.add("b");

        assertThat(buffer.peekOldest()).contains("a");
        buffer.removeOldest();
        assertThat(buffer.peekOldest()).contains("b");
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("가득 찬 상태에서 넣으면 가장 오래된 것을 버리고, 버렸다고 알려준다")
    void dropsOldestWhenFull() {
        TelemetryBuffer<String> buffer = new TelemetryBuffer<>(2);
        assertThat(buffer.add("a")).isFalse();
        assertThat(buffer.add("b")).isFalse();

        boolean dropped = buffer.add("c");

        assertThat(dropped).isTrue();
        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.peekOldest()).contains("b");
    }

    @Test
    @DisplayName("비어 있으면 꺼낼 것이 없다")
    void emptyBufferHasNothingToPeek() {
        TelemetryBuffer<String> buffer = new TelemetryBuffer<>(2);

        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.peekOldest()).isEmpty();
    }
}

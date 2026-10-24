package vent.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 측정값 생성기 시나리오 단위 테스트.
 *
 * <p>테스트 지점: 생성기의 공개 메서드. 필터 막힘은 측정할 때마다 서서히 올라 180Pa에 닿고 그 근처에 머물며,
 * 필터 교체는 바로 60Pa로 돌아오는지 본다(#4).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
class TelemetryGeneratorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 24, 1, 0, 0, 0, ZoneOffset.UTC);

    private final TelemetryGenerator generator = new TelemetryGenerator("vent-s1", 210, 1);

    @Test
    @DisplayName("필터 막힘을 일으키면 차압이 한 번에 뛰지 않고 서서히 올라 180Pa에 닿는다")
    void filterClogRisesGraduallyToTarget() {
        int before = generator.next(NOW).filterPa();
        generator.clogFilter();

        int first = generator.next(NOW).filterPa();
        assertThat(first).isGreaterThan(before).isLessThan(180);

        int last = first;
        for (int i = 0; i < 30; i++) {
            last = generator.next(NOW).filterPa();
        }
        assertThat(last).isEqualTo(180);
    }

    @Test
    @DisplayName("필터를 교체하면 다음 측정값부터 60Pa로 돌아온다")
    void filterReplaceResetsPressure() {
        generator.clogFilter();
        for (int i = 0; i < 30; i++) {
            generator.next(NOW);
        }

        generator.replaceFilter();

        assertThat(generator.next(NOW).filterPa()).isEqualTo(60);
    }
}

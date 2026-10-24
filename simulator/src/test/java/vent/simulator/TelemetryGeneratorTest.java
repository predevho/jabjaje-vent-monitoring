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
 * 필터 교체는 바로 60Pa로 돌아오는지 본다(#4). CO2 상승은 1,500ppm을 넘었다가 정상으로 돌아오고, 풍량 저하는
 * 목표 풍량의 80% 아래로, 바이패스는 측정값에 그대로 실리는지 본다(#5).
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

    @Test
    @DisplayName("CO2 상승은 서서히 1,500ppm을 넘어 1,600ppm에 머물고, 정상으로 돌리면 해제 기준(900) 아래에 머문다")
    void co2RisesAboveCriticalAndComesBack() {
        generator.raiseCo2();
        int first = generator.next(NOW).co2Ppm();
        assertThat(first).isLessThan(1000);
        int last = first;
        for (int i = 0; i < 30; i++) {
            last = generator.next(NOW).co2Ppm();
        }
        assertThat(last).isEqualTo(1600);

        generator.normalizeCo2();
        for (int i = 0; i < 9; i++) {
            generator.next(NOW);
        }
        // 내려온 뒤로는 계속 900ppm 미만이어야 CO2 과다가 해제된다(900 미만이 10분 지속).
        for (int i = 0; i < 40; i++) {
            assertThat(generator.next(NOW).co2Ppm()).isLessThan(900);
        }
    }

    @Test
    @DisplayName("풍량 저하면 급기가 목표 풍량(210)의 80%(168) 아래로, 정상으로 돌리면 다시 위로")
    void airflowLowGoesBelowEightyPercent() {
        generator.lowerAirflow();
        assertThat(generator.next(NOW).supplyCmh()).isLessThan(168);

        generator.restoreAirflow();
        assertThat(generator.next(NOW).supplyCmh()).isGreaterThanOrEqualTo(168);
    }

    @Test
    @DisplayName("바이패스를 켜면 측정값에 바이패스가 실린다")
    void bypassIsReported() {
        generator.setBypass(true);
        assertThat(generator.next(NOW).bypass()).isTrue();

        generator.setBypass(false);
        assertThat(generator.next(NOW).bypass()).isFalse();
    }
}

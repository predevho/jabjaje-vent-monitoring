package vent.anomaly.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalySeverity;

/**
 * 이상 심각도 올리기 단위 테스트.
 *
 * <p>테스트 지점: 이상 엔티티의 공개 메서드. 스펙 6.2: 더 무거운 심각도면 올리고, 해결될 때까지 내리지 않는다(#5).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
class AnomalyTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("주의 → 심각으로 올리면 설명도 바뀌고, 다시 주의가 와도 심각을 유지한다")
    void escalatesButNeverDowngrades() {
        Anomaly anomaly = Anomaly.openForDevice("vent-s1", AnomalyRuleName.CO2_HIGH, AnomalySeverity.WARN, "CO2 1,100ppm", T0);

        assertThat(anomaly.escalate(AnomalySeverity.CRITICAL, "CO2 1,600ppm")).isTrue();
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(anomaly.getMessage()).isEqualTo("CO2 1,600ppm");

        assertThat(anomaly.escalate(AnomalySeverity.WARN, "CO2 1,200ppm")).isFalse();
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(anomaly.getMessage()).isEqualTo("CO2 1,600ppm");
    }

    @Test
    @DisplayName("해결된 이상은 심각도를 바꾸지 않는다")
    void resolvedAnomalyIsNotEscalated() {
        Anomaly anomaly = Anomaly.openForDevice("vent-s1", AnomalyRuleName.CO2_HIGH, AnomalySeverity.WARN, "CO2 1,100ppm", T0);
        anomaly.resolve(T0.plusMinutes(20));

        assertThat(anomaly.escalate(AnomalySeverity.CRITICAL, "CO2 1,600ppm")).isFalse();
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.WARN);
    }
}

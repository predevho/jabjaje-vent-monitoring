package vent.site.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 최소 환기량 계산 단위 테스트.
 *
 * <p>테스트 지점: 현장의 최소 환기량 계산식. 기대값은 스펙 5.3의 식(인원 × 30CMH와 면적 × 2.5m × 0.6회/h 중 큰 값을
 * 올림)을 손으로 계산한 숫자다. 목표 풍량(TAB 값 우선)은 시드 검증(SiteListApiTest)이 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#5)
 */
class SiteMinimumAirflowTest {

    @Test
    @DisplayName("면적 기준이 크면 면적 기준을 정수로 올린다: 99m² → 148.5 → 149")
    void areaBasisRoundsUp() {
        assertThat(Site.minimumAirflowCmh(3, new BigDecimal("99.0"))).isEqualTo(149);
    }

    @Test
    @DisplayName("인원 기준이 크면 인원 기준: 6명 × 30 = 180 > 40m² × 1.5 = 60")
    void occupantBasisWhenLarger() {
        assertThat(Site.minimumAirflowCmh(6, new BigDecimal("40.0"))).isEqualTo(180);
    }

    @Test
    @DisplayName("딱 떨어지면 올리지 않는다: 120m² × 1.5 = 180")
    void exactValueIsNotRoundedUp() {
        assertThat(Site.minimumAirflowCmh(2, new BigDecimal("120.0"))).isEqualTo(180);
    }
}

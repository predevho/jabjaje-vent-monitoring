package vent.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vent.telemetry.domain.type.TelemetryQuality;
import vent.telemetry.dto.request.TelemetryMessage;

/**
 * 측정값 품질 판정기 단위 테스트.
 *
 * <p>테스트 지점: 품질 판정기의 공개 메서드. 기준값은 스펙 5.2와 #3에서 정한 값(늦음 2분, 시계 오차 5분, 범위)이고,
 * 경계값(정확히 기준값, 기준값 + 1초)을 직접 적은 숫자로 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
class TelemetryQualityClassifierTest {

    private static final OffsetDateTime RECEIVED_AT = OffsetDateTime.of(2026, 10, 24, 1, 0, 0, 0, ZoneOffset.UTC);

    private final TelemetryQualityClassifier classifier = new TelemetryQualityClassifier(new TelemetryQualityProperties(
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            new TelemetryQualityProperties.Range(1, 10_000),
            new TelemetryQualityProperties.Range(-40, 60),
            new TelemetryQualityProperties.Range(0, 100),
            new TelemetryQualityProperties.Range(0, 2_000),
            new TelemetryQualityProperties.Range(0, 2_000)));

    @Test
    @DisplayName("정상 범위 값을 바로 받으면 OK이고 늦지 않다")
    void normalTelemetryIsOkAndOnTime() {
        TelemetryClassification result = classifier.classify(message(RECEIVED_AT, 820, "23.4", "48"), RECEIVED_AT);

        assertThat(result.quality()).isEqualTo(TelemetryQuality.OK);
        assertThat(result.late()).isFalse();
    }

    @Nested
    @DisplayName("늦은 측정값")
    class Late {

        @Test
        @DisplayName("수신이 측정보다 정확히 2분 늦으면 늦은 측정값이 아니다")
        void exactlyTwoMinutesIsNotLate() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.minusMinutes(2), 820, "23.4", "48"), RECEIVED_AT);

            assertThat(result.late()).isFalse();
        }

        @Test
        @DisplayName("2분 1초 늦으면 늦은 측정값이다. 품질은 그대로 OK")
        void moreThanTwoMinutesIsLate() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.minusMinutes(2).minusSeconds(1), 820, "23.4", "48"), RECEIVED_AT);

            assertThat(result.late()).isTrue();
            assertThat(result.quality()).isEqualTo(TelemetryQuality.OK);
        }
    }

    @Nested
    @DisplayName("시계 오차")
    class ClockSkew {

        @Test
        @DisplayName("측정 시각이 수신보다 정확히 5분 미래면 시계 오차가 아니다")
        void exactlyFiveMinutesAheadIsOk() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.plusMinutes(5), 820, "23.4", "48"), RECEIVED_AT);

            assertThat(result.quality()).isEqualTo(TelemetryQuality.OK);
        }

        @Test
        @DisplayName("5분 1초 미래면 CLOCK_SKEW이고, 시각을 믿을 수 없으니 늦음 판정도 하지 않는다")
        void moreThanFiveMinutesAheadIsClockSkew() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.plusMinutes(5).plusSeconds(1), 820, "23.4", "48"), RECEIVED_AT);

            assertThat(result.quality()).isEqualTo(TelemetryQuality.CLOCK_SKEW);
            assertThat(result.late()).isFalse();
        }

        @Test
        @DisplayName("시계 오차와 범위 밖이 같이 있으면 시계 오차를 우선한다")
        void clockSkewWinsOverOutOfRange() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.plusMinutes(10), 0, "23.4", "48"), RECEIVED_AT);

            assertThat(result.quality()).isEqualTo(TelemetryQuality.CLOCK_SKEW);
        }
    }

    @Nested
    @DisplayName("범위 밖")
    class OutOfRange {

        @Test
        @DisplayName("CO2 0ppm은 센서 고장값이라 범위 밖이다")
        void zeroCo2IsOutOfRange() {
            assertThat(classifier.classify(message(RECEIVED_AT, 0, "23.4", "48"), RECEIVED_AT).quality())
                    .isEqualTo(TelemetryQuality.OUT_OF_RANGE);
        }

        @Test
        @DisplayName("CO2 10,000ppm은 범위 안이다")
        void co2UpperBoundIsInRange() {
            assertThat(classifier.classify(message(RECEIVED_AT, 10_000, "23.4", "48"), RECEIVED_AT).quality())
                    .isEqualTo(TelemetryQuality.OK);
        }

        @Test
        @DisplayName("습도 100.1%는 범위 밖이다")
        void humidityAboveHundredIsOutOfRange() {
            assertThat(classifier.classify(message(RECEIVED_AT, 820, "23.4", "100.1"), RECEIVED_AT).quality())
                    .isEqualTo(TelemetryQuality.OUT_OF_RANGE);
        }

        @Test
        @DisplayName("온도 -40.1℃는 범위 밖이다")
        void temperatureBelowMinimumIsOutOfRange() {
            assertThat(classifier.classify(message(RECEIVED_AT, 820, "-40.1", "48"), RECEIVED_AT).quality())
                    .isEqualTo(TelemetryQuality.OUT_OF_RANGE);
        }

        @Test
        @DisplayName("늦게 온 범위 밖 값은 OUT_OF_RANGE이면서 늦은 측정값이다")
        void lateAndOutOfRangeAreIndependent() {
            TelemetryClassification result = classifier.classify(
                    message(RECEIVED_AT.minusMinutes(10), 0, "23.4", "48"), RECEIVED_AT);

            assertThat(result.quality()).isEqualTo(TelemetryQuality.OUT_OF_RANGE);
            assertThat(result.late()).isTrue();
        }
    }

    private static TelemetryMessage message(OffsetDateTime measuredAt, int co2Ppm, String tempC, String humidityPct) {
        return new TelemetryMessage(1, UUID.randomUUID(), "vent-s1", measuredAt,
                co2Ppm, new BigDecimal(tempC), new BigDecimal(humidityPct), 205, 200, 85, false);
    }
}

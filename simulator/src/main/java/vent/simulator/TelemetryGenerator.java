package vent.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * 열회수 환기장치 한 대의 측정값 생성기.
 *
 * <p>정상 운전 상태를 흉내 낸다. 값마다 이전 값에서 조금씩 움직이는 방식(랜덤 워크)이라 그래프가 자연스럽다.
 * 필터 차압은 아주 천천히 오른다(먼지가 쌓이는 모습). 이상 시나리오는 이후 티켓(#4, #5)에서 추가한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public class TelemetryGenerator {

    private final String ventilatorId;
    private final int targetAirflowCmh;
    private final Random random;

    private double co2Ppm;
    private double tempC;
    private double humidityPct;
    private double filterPa;

    public TelemetryGenerator(String ventilatorId, int targetAirflowCmh, long seed) {
        this.ventilatorId = ventilatorId;
        this.targetAirflowCmh = targetAirflowCmh;
        // java.util.Random은 시드가 가까우면(1, 2, 3) 처음 값들이 거의 같아서, 시드를 섞어 현장마다 다르게 움직이게 한다.
        this.random = new Random(seed * 0x9E3779B97F4A7C15L);
        // 현장마다 시작값도 조금씩 다르게 둔다.
        this.co2Ppm = 600 + random.nextInt(150);
        this.tempC = 22 + random.nextDouble();
        this.humidityPct = 43 + random.nextInt(6);
        this.filterPa = 60 + random.nextInt(20);
    }

    /** 다음 측정값 하나를 만든다. */
    public TelemetryMessage next(OffsetDateTime measuredAt) {
        co2Ppm = clamp(co2Ppm + drift(15), 500, 950);
        tempC = clamp(tempC + drift(0.1), 21, 25);
        humidityPct = clamp(humidityPct + drift(0.5), 40, 55);
        filterPa = filterPa + 0.02;

        int supplyCmh = (int) Math.round(targetAirflowCmh * 0.98 + drift(2));
        int exhaustCmh = (int) Math.round(supplyCmh * 0.97);

        return new TelemetryMessage(
                TelemetryMessage.SCHEMA_VERSION,
                UUID.randomUUID(),
                ventilatorId,
                measuredAt,
                (int) Math.round(co2Ppm),
                oneDecimal(tempC),
                oneDecimal(humidityPct),
                supplyCmh,
                exhaustCmh,
                (int) Math.round(filterPa),
                false);
    }

    private double drift(double maxStep) {
        return (random.nextDouble() * 2 - 1) * maxStep;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal oneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}

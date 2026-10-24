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
 * 필터 차압은 아주 천천히 오른다(먼지가 쌓이는 모습). 이상 시나리오를 켤 수 있다: 필터 막힘·교체(#4),
 * CO2 상승, 풍량 저하, 바이패스 지속(#5).
 * 게이트웨이 스레드 하나에서만 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public class TelemetryGenerator {

    private final String ventilatorId;
    private final int targetAirflowCmh;
    private final Random random;

    /**
     * 필터 막힘 시나리오에서 차압이 향하는 값과, 측정할 때마다 오르는 폭 (#4).
     * 실제로는 몇 주에 걸쳐 오르지만, 시연에서 1~2분 안에 기준(150Pa)을 넘도록 크게 잡았다.
     */
    private static final double CLOGGED_FILTER_PA = 180;
    private static final double CLOG_STEP_PA = 30;
    private static final double NEW_FILTER_PA = 60;
    /**
     * CO2 상승 시나리오에서 CO2가 향하는 값과 측정할 때마다 오르는 폭 (#5). 1,000ppm(주의)과 1,500ppm(심각)을 차례로 넘는다.
     * 정상으로 돌아갈 때는 측정할 때마다 100ppm씩 내려 평소 수준(750ppm)까지 온다. 정상 범위 끝 근처에 멈추면
     * 해제 기준(900ppm 미만)을 오르내려 이상이 해제되지 않는다(Docker 시연에서 발견).
     */
    private static final double HIGH_CO2_PPM = 1600;
    private static final double CO2_RISE_STEP_PPM = 60;
    private static final double CO2_FALL_STEP_PPM = 100;
    /** 평소 CO2가 움직이는 상한. CO2 과다 해제 기준(900ppm 미만)보다 낮게 둬서 정상일 때 해제가 확실히 된다 */
    private static final double NORMAL_CO2_MAX_PPM = 880;
    private static final double SETTLED_CO2_PPM = 750;
    /** 풍량 저하 시나리오에서 급기가 목표 풍량의 몇 배가 되는지 (#5). 80% 기준보다 낮다 */
    private static final double LOW_AIRFLOW_RATIO = 0.6;
    private static final double NORMAL_AIRFLOW_RATIO = 0.98;
    /** 후드 연동 중 급기·배기 배율 (#7). VEMC가 급기를 올리고 배기를 내려 실내 압력을 맞춘다(용어집: 후드 연동) */
    private static final double HOOD_SUPPLY_FACTOR = 1.4;
    private static final double HOOD_EXHAUST_FACTOR = 0.8;

    private double co2Ppm;
    private double tempC;
    private double humidityPct;
    private double filterPa;
    /** 필터 막힘 시나리오가 켜져 있으면 true */
    private boolean filterClogging;
    /** CO2 상승 시나리오가 켜져 있으면 true (#5) */
    private boolean co2Rising;
    /** CO2가 평소 수준으로 내려오는 중이면 true (#5) */
    private boolean co2Settling;
    /** 풍량 저하 시나리오가 켜져 있으면 true (#5) */
    private boolean airflowLow;
    /** 바이패스가 켜져 있으면 true (#5) */
    private boolean bypass;
    /** VEMC가 후드 연동 신호를 보내고 있으면 true (#7) */
    private boolean hoodInterlock;

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
        co2Ppm = nextCo2();
        tempC = clamp(tempC + drift(0.1), 21, 25);
        humidityPct = clamp(humidityPct + drift(0.5), 40, 55);
        // 평소에는 먼지가 쌓이듯 아주 천천히, 막힘 시나리오에서는 측정할 때마다 크게 오른다.
        filterPa = filterClogging ? Math.min(CLOGGED_FILTER_PA, filterPa + CLOG_STEP_PA) : filterPa + 0.02;

        double airflowRatio = airflowLow ? LOW_AIRFLOW_RATIO : NORMAL_AIRFLOW_RATIO;
        int baseSupplyCmh = (int) Math.round(targetAirflowCmh * airflowRatio + drift(2));
        int supplyCmh = hoodInterlock ? (int) Math.round(baseSupplyCmh * HOOD_SUPPLY_FACTOR) : baseSupplyCmh;
        int exhaustCmh = (int) Math.round(baseSupplyCmh * 0.97 * (hoodInterlock ? HOOD_EXHAUST_FACTOR : 1));

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
                bypass);
    }

    /**
     * 필터 막힘 시나리오: 다음 측정부터 차압이 측정할 때마다 30Pa씩 올라 180Pa에 머문다.
     *
     * @since 2026-10-24 (#4)
     */
    public void clogFilter() {
        filterClogging = true;
    }

    /**
     * 필터 교체 시나리오: 차압이 새 필터 수준(60Pa)으로 돌아가고, 다시 천천히 쌓인다.
     *
     * @since 2026-10-24 (#4)
     */
    public void replaceFilter() {
        filterClogging = false;
        filterPa = NEW_FILTER_PA;
    }

    /** CO2 상승 중이면 오르고, 정상으로 돌리면 평소 수준까지 내려오고, 아니면 정상 범위에서 조금씩 움직인다. */
    private double nextCo2() {
        if (co2Rising) {
            return Math.min(HIGH_CO2_PPM, co2Ppm + CO2_RISE_STEP_PPM);
        }
        if (co2Settling) {
            double next = Math.max(SETTLED_CO2_PPM, co2Ppm - CO2_FALL_STEP_PPM);
            co2Settling = next > SETTLED_CO2_PPM;
            return next;
        }
        return clamp(co2Ppm + drift(15), 500, NORMAL_CO2_MAX_PPM);
    }

    /**
     * CO2 상승 시나리오: 재실 인원이 많아진 것처럼 측정할 때마다 60ppm씩 올라 1,600ppm에 머문다.
     *
     * @since 2026-10-24 (#5)
     */
    public void raiseCo2() {
        co2Rising = true;
    }

    /**
     * CO2 정상 시나리오: 측정할 때마다 100ppm씩 내려 평소 수준(750ppm)으로 돌아온다.
     *
     * @since 2026-10-24 (#5)
     */
    public void normalizeCo2() {
        co2Rising = false;
        co2Settling = co2Ppm > SETTLED_CO2_PPM;
    }

    /**
     * 풍량 저하 시나리오: 급기가 목표 풍량의 약 60%로 떨어진다(팬 설정이 낮아진 경우).
     *
     * @since 2026-10-24 (#5)
     */
    public void lowerAirflow() {
        airflowLow = true;
    }

    /**
     * 풍량 정상 시나리오: 급기가 목표 풍량의 약 98%로 돌아온다.
     *
     * @since 2026-10-24 (#5)
     */
    public void restoreAirflow() {
        airflowLow = false;
    }

    /**
     * 바이패스 켜기/끄기 시나리오 (#5).
     *
     * @since 2026-10-24 (#5)
     */
    public void setBypass(boolean on) {
        bypass = on;
    }

    /**
     * 후드 연동 신호를 켜거나 끈다. 켜져 있는 동안 급기는 약 40% 오르고 배기는 약 20% 내린다.
     * 서버를 거치지 않고 VEMC와 환기장치 사이에서 바로 일어난다(ADR-0001).
     *
     * @since 2026-10-24 (#7)
     */
    public void setHoodInterlock(boolean on) {
        hoodInterlock = on;
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

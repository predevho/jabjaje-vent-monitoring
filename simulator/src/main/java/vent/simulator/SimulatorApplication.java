package vent.simulator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 가상 게이트웨이(시뮬레이터)의 진입점.
 *
 * <p>현장 3곳의 게이트웨이를 흉내 내어, 현장마다 MQTT 연결 하나로 열회수 환기장치의 측정값을 주기적으로 발행한다(스펙 3.3).
 * 현장 목록은 서버의 시연용 시드 데이터(db/seed/R__demo_seed.sql)와 같다.
 *
 * <p>환경변수: {@code MQTT_URI}(기본 {@code tcp://localhost:1883}), {@code INTERVAL_SECONDS}(기본 30).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public final class SimulatorApplication {

    private static final List<SiteConfig> SITES = List.of(
            new SiteConfig("1", "gw-s1", "vent-s1", 210),
            new SiteConfig("2", "gw-s2", "vent-s2", 149),
            new SiteConfig("3", "gw-s3", "vent-s3", 248));

    private SimulatorApplication() {
    }

    public static void main(String[] args) {
        String brokerUri = env("MQTT_URI", "tcp://localhost:1883");
        Duration interval = Duration.ofSeconds(Long.parseLong(env("INTERVAL_SECONDS", "30")));

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(SITES.size());
        List<GatewaySimulator> gateways = SITES.stream()
                .map(site -> new GatewaySimulator(site, brokerUri))
                .toList();

        for (GatewaySimulator gateway : gateways) {
            scheduler.execute(gateway::connect);
            scheduler.scheduleAtFixedRate(gateway::publishTelemetry,
                    interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        }
        Log.info("시뮬레이터 시작: 현장 %d곳, 브로커 %s, 주기 %d초", SITES.size(), brokerUri, interval.toSeconds());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            gateways.forEach(GatewaySimulator::close);
        }));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

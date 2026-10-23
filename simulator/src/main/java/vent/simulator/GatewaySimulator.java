package vent.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

/**
 * 현장 게이트웨이 하나의 시뮬레이터.
 *
 * <p>실제 구조에서 게이트웨이는 환기장치를 Modbus로 읽어 MQTT로 클라우드에 보낸다. 여기서는 측정값을 만들어
 * {@code jabjaje/{현장ID}/{장비ID}/telemetry} 토픽에 QoS 1로 발행한다(스펙 4.1). 측정값을 보내는 장비는 열회수 환기장치다.
 * 연결이 끊긴 동안의 버퍼와 재접속 백오프는 #3에서 추가한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public class GatewaySimulator {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int QOS_AT_LEAST_ONCE = 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final int CONNECT_RETRY_SECONDS = 5;
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SiteConfig site;
    private final MqttClient client;
    private final TelemetryGenerator generator;

    public GatewaySimulator(SiteConfig site, String brokerUri) {
        this.site = site;
        this.generator = new TelemetryGenerator(site.ventilatorId(), site.targetAirflowCmh(), site.siteId().hashCode());
        try {
            this.client = new MqttClient(brokerUri, site.gatewayId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("잘못된 브로커 주소: " + brokerUri, e);
        }
    }

    /** 브로커에 연결될 때까지 5초 간격으로 재시도한다. 연결된 뒤의 재연결은 Paho 자동 재연결이 맡는다. */
    public void connect() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        while (!client.isConnected() && !Thread.currentThread().isInterrupted()) {
            try {
                client.connect(options);
                Log.info("[%s] 브로커 연결됨", site.gatewayId());
            } catch (MqttException e) {
                Log.error("[%s] 브로커 연결 실패, %d초 뒤 재시도: %s", site.gatewayId(), CONNECT_RETRY_SECONDS, e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(CONNECT_RETRY_SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** 측정값 하나를 만들어 발행한다. 연결되어 있지 않으면 이번 주기는 건너뛴다. */
    public void publishTelemetry() {
        if (!client.isConnected()) {
            Log.info("[%s] 연결 안 됨, 이번 측정값 건너뜀", site.gatewayId());
            return;
        }
        TelemetryMessage telemetry = generator.next(OffsetDateTime.now(SEOUL));
        try {
            MqttMessage message = new MqttMessage(JSON.writeValueAsBytes(telemetry));
            message.setQos(QOS_AT_LEAST_ONCE);
            client.publish(topic(), message);
            Log.info("[%s] 측정값 발행: CO2 %d, 급기 %d CMH, 필터 %d Pa",
                    site.gatewayId(), telemetry.co2Ppm(), telemetry.supplyCmh(), telemetry.filterPa());
        } catch (JsonProcessingException | MqttException e) {
            // 예약 작업에서 예외가 나가면 다음 주기가 멈추므로 여기서 잡는다.
            Log.error("[%s] 측정값 발행 실패: %s", site.gatewayId(), e.getMessage());
        }
    }

    public void close() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            Log.error("[%s] 연결 종료 중 오류: %s", site.gatewayId(), e.getMessage());
        }
    }

    private String topic() {
        return "jabjaje/" + site.siteId() + "/" + site.ventilatorId() + "/telemetry";
    }
}

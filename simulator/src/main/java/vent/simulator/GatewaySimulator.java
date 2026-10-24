package vent.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

/**
 * 현장 게이트웨이 하나의 시뮬레이터.
 *
 * <p>실제 구조에서 게이트웨이는 환기장치를 Modbus로 읽어 MQTT로 클라우드에 보낸다. 여기서는 측정값을 만들어
 * {@code jabjaje/{현장ID}/{장비ID}/telemetry} 토픽에 QoS 1로 발행한다(스펙 4.1). 측정값을 보내는 장비는 열회수 환기장치다.
 *
 * <p>측정값은 연결 상태와 상관없이 주기마다 만들어 {@link TelemetryBuffer}에 넣고, 연결되어 있으면 오래된 것부터 보낸다.
 * 연결이 끊긴 동안 쌓인 측정값은 다시 연결된 뒤 원래 측정 시각 그대로 올라가고, 서버는 이를 늦은 측정값으로 표시한다(ADR-0003).
 * 재접속은 {@link ReconnectBackoff}(지수 백오프 + jitter)로 한다. 모든 작업은 게이트웨이마다 스레드 하나에서 순서대로 돈다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public class GatewaySimulator {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int QOS_AT_LEAST_ONCE = 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    /** 30초 주기로 24시간 분량 */
    private static final int BUFFER_CAPACITY = 2_880;
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SiteConfig site;
    private final MqttClient client;
    private final TelemetryGenerator generator;
    private final TelemetryBuffer<TelemetryMessage> buffer = new TelemetryBuffer<>(BUFFER_CAPACITY);
    private final ReconnectBackoff backoff = new ReconnectBackoff();
    private final ScheduledExecutorService worker;

    public GatewaySimulator(SiteConfig site, String brokerUri) {
        this.site = site;
        this.generator = new TelemetryGenerator(site.ventilatorId(), site.targetAirflowCmh(), site.siteId().hashCode());
        this.worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, site.gatewayId()));
        try {
            this.client = new MqttClient(brokerUri, site.gatewayId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("잘못된 브로커 주소: " + brokerUri, e);
        }
        this.client.setCallback(new GatewayCallback());
    }

    /** 연결을 시작하고, 주기마다 측정값을 만들도록 예약한다. */
    public void start(Duration interval) {
        worker.execute(this::tryConnect);
        worker.scheduleAtFixedRate(this::tick, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 한 번 연결을 시도한다. 실패하면 백오프만큼 기다렸다가 다시 시도하도록 예약한다(기다리는 동안에도 측정값은 계속 쌓인다). */
    private void tryConnect() {
        if (client.isConnected()) {
            return;
        }
        try {
            client.connect(connectionOptions());
            backoff.reset();
            Log.info("[%s] 브로커 연결됨, 쌓인 측정값 %d개", site.gatewayId(), buffer.size());
            flush();
        } catch (MqttException | RuntimeException e) {
            // 예상하지 못한 예외에서도 다음 시도를 예약해야 게이트웨이가 영구히 끊긴 채로 남지 않는다.
            Duration delay = backoff.nextDelay();
            Log.error("[%s] 브로커 연결 실패, %.1f초 뒤 재시도: %s",
                    site.gatewayId(), delay.toMillis() / 1000.0, e.getMessage());
            worker.schedule(this::tryConnect, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private MqttConnectionOptions connectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        // Paho 자동 재연결에는 jitter가 없어 직접 재접속한다.
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        options.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);
        return options;
    }

    /** 측정값 하나를 만들어 버퍼에 넣고, 연결되어 있으면 쌓인 것을 보낸다. */
    private void tick() {
        TelemetryMessage telemetry = generator.next(OffsetDateTime.now(SEOUL));
        if (buffer.add(telemetry)) {
            Log.error("[%s] 버퍼가 가득 차 가장 오래된 측정값을 버림", site.gatewayId());
        }
        if (client.isConnected()) {
            flush();
        } else {
            Log.info("[%s] 연결 끊김, 측정값을 버퍼에 쌓음 (%d개)", site.gatewayId(), buffer.size());
        }
    }

    /**
     * 쌓인 측정값을 오래된 것부터 하나씩 보낸다. 동기 발행이라 브로커가 받았다고 응답(PUBACK)한 뒤에야 버퍼에서 지운다.
     * 보내다 실패하면 남은 것은 버퍼에 그대로 두고 다음 기회에 보낸다.
     */
    private void flush() {
        int sent = 0;
        Optional<TelemetryMessage> next;
        while (client.isConnected() && (next = buffer.peekOldest()).isPresent()) {
            TelemetryMessage telemetry = next.get();
            try {
                MqttMessage message = new MqttMessage(JSON.writeValueAsBytes(telemetry));
                message.setQos(QOS_AT_LEAST_ONCE);
                client.publish(topic(), message);
                buffer.removeOldest();
                sent++;
            } catch (JsonProcessingException e) {
                // 직렬화가 안 되는 값은 다시 해도 안 되므로 버린다.
                Log.error("[%s] 측정값 직렬화 실패, 버림: %s", site.gatewayId(), e.getMessage());
                buffer.removeOldest();
            } catch (MqttException e) {
                Log.error("[%s] 측정값 발행 실패, 버퍼에 남김 (%d개): %s", site.gatewayId(), buffer.size(), e.getMessage());
                return;
            }
        }
        if (sent > 1) {
            Log.info("[%s] 쌓여 있던 측정값 %d개 발행", site.gatewayId(), sent);
        } else if (sent == 1) {
            Log.info("[%s] 측정값 발행", site.gatewayId());
        }
    }

    public void close() {
        worker.shutdownNow();
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

    /**
     * 게이트웨이의 MQTT 콜백. 연결이 끊기면 재접속을 예약하고, 오류는 로그로 남긴다. 나머지 콜백은 아직 쓰지 않는다.
     *
     * @author predevho (AI 보조: Claude)
     * @since 2026-10-24 (#3)
     */
    private final class GatewayCallback implements MqttCallback {

        @Override
        public void disconnected(MqttDisconnectResponse response) {
            Log.error("[%s] 브로커 연결 끊김: %s", site.gatewayId(), response.getReasonString());
            worker.execute(GatewaySimulator.this::tryConnect);
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            Log.error("[%s] MQTT 오류: %s", site.gatewayId(), exception.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // 아직 구독하는 토픽이 없다. 원격 명령은 #8에서 받는다.
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
        }
    }
}

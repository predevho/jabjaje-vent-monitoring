package vent.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
 * <p>연결할 때 상태 토픽에 {@code online}과 버전·지원 기능을 retained로 보고하고, Last Will로 {@code offline}을 등록한다(#6).
 * 연결이 갑자기 끊기면 브로커가 이 offline을 대신 보낸다.
 *
 * <p>현장의 VEMC도 흉내 낸다(#7). 후드가 켜지면 HOOD_ON 이벤트를 {@code jabjaje/{현장ID}/{VEMC ID}/event}로 보내고,
 * 후드 연동(급기 올림, 배기 내림)은 브로커를 거치지 않고 여기서 바로 처리한다(ADR-0001). 이벤트도 끊긴 동안 버퍼에 쌓는다.
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
    private static final int EVENT_BUFFER_CAPACITY = 500;
    /** online 상태 메시지로 보고하는 버전과 지원 기능 (스펙 4.4, #6) */
    private static final String FIRMWARE_VERSION = "1.0.0";
    private static final List<String> CAPABILITIES = List.of("BOOST", "HOOD_EVENT");
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SiteConfig site;
    private final MqttClient client;
    private final TelemetryGenerator generator;
    private final TelemetryBuffer<TelemetryMessage> buffer = new TelemetryBuffer<>(BUFFER_CAPACITY);
    /** 후드 이벤트 버퍼 (#7). 측정값보다 드물어 작게 둔다 */
    private final TelemetryBuffer<DeviceEventMessage> eventBuffer = new TelemetryBuffer<>(EVENT_BUFFER_CAPACITY);
    private final ReconnectBackoff backoff = new ReconnectBackoff();
    private final ScheduledExecutorService worker;
    /** 네트워크 끊김 시나리오(#6)가 켜져 있으면 true. 켜져 있는 동안은 다시 연결하지 않는다. 게이트웨이 스레드에서만 쓴다. */
    private boolean networkDown;
    /** VEMC 고장 시나리오(#7). 후드는 감지해 이벤트는 보내지만 환기장치로 신호가 가지 않는다(배선 불량). 게이트웨이 스레드에서만 쓴다 */
    private boolean vemcBroken;
    /** 후드가 켜져 있으면 true (#7). 이미 켜진 후드에 HOOD_ON을 다시 보내지 않고, VEMC를 고치면 연동을 다시 켠다 */
    private boolean hoodOn;

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
        if (networkDown || client.isConnected()) {
            return;
        }
        try {
            client.connect(connectionOptions());
            // online을 못 보내면 브로커에는 예전 Last Will(offline)이 retained로 남는다. 연결을 끊고 처음부터 다시 시도한다.
            publishStatus(onlineStatusJson());
            backoff.reset();
            Log.info("[%s] 브로커 연결됨, 쌓인 측정값 %d개", site.gatewayId(), buffer.size());
            flush();
        } catch (MqttException | RuntimeException e) {
            dropConnectionQuietly();
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
        // 연결이 갑자기 끊기면 브로커가 대신 보내는 offline (Last Will, retained)
        MqttMessage will = new MqttMessage(offlineStatusJson());
        will.setQos(QOS_AT_LEAST_ONCE);
        will.setRetained(true);
        options.setWill(statusTopic(), will);
        return options;
    }

    /** 상태 토픽에 retained로 보고한다. 서버가 나중에 구독해도 마지막 상태를 받을 수 있다. */
    private void publishStatus(byte[] json) throws MqttException {
        MqttMessage message = new MqttMessage(json);
        message.setQos(QOS_AT_LEAST_ONCE);
        message.setRetained(true);
        client.publish(statusTopic(), message);
    }

    /** online: 버전과 지원 기능을 함께 보고한다 (스펙 4.4). */
    private static byte[] onlineStatusJson() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", "online");
        status.put("firmwareVersion", FIRMWARE_VERSION);
        status.put("schemaVersion", TelemetryMessage.SCHEMA_VERSION);
        status.put("capabilities", CAPABILITIES);
        return toJson(status);
    }

    private static byte[] offlineStatusJson() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", "offline");
        status.put("schemaVersion", TelemetryMessage.SCHEMA_VERSION);
        return toJson(status);
    }

    private static byte[] toJson(Map<String, Object> status) {
        try {
            return JSON.writeValueAsBytes(status);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상태 메시지를 만들 수 없음", e);
        }
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

    /** 쌓인 이벤트와 측정값을 보낸다. */
    private void flush() {
        flush(eventBuffer, eventTopic(), "이벤트");
        flush(buffer, topic(), "측정값");
    }

    /**
     * 버퍼에 쌓인 메시지를 오래된 것부터 하나씩 보낸다. 동기 발행이라 브로커가 받았다고 응답(PUBACK)한 뒤에야 버퍼에서 지운다.
     * 보내다 실패하면 남은 것은 버퍼에 그대로 두고 다음 기회에 보낸다.
     */
    private <T> void flush(TelemetryBuffer<T> pending, String topic, String label) {
        int sent = 0;
        Optional<T> next;
        while (client.isConnected() && (next = pending.peekOldest()).isPresent()) {
            try {
                MqttMessage message = new MqttMessage(JSON.writeValueAsBytes(next.get()));
                message.setQos(QOS_AT_LEAST_ONCE);
                client.publish(topic, message);
                pending.removeOldest();
                sent++;
            } catch (JsonProcessingException e) {
                // 직렬화가 안 되는 값은 다시 해도 안 되므로 버린다.
                Log.error("[%s] %s 직렬화 실패, 버림: %s", site.gatewayId(), label, e.getMessage());
                pending.removeOldest();
            } catch (MqttException e) {
                Log.error("[%s] %s 발행 실패, 버퍼에 남김 (%d개): %s", site.gatewayId(), label, pending.size(), e.getMessage());
                return;
            }
        }
        if (sent > 1) {
            Log.info("[%s] 쌓여 있던 %s %d개 발행", site.gatewayId(), label, sent);
        } else if (sent == 1) {
            Log.info("[%s] %s 발행", site.gatewayId(), label);
        }
    }

    /**
     * 후드 켜짐 시나리오: VEMC가 HOOD_ON을 보내고, 고장이 아니면 환기장치 급기를 올린다(현장 연동, ADR-0001).
     *
     * @since 2026-10-24 (#7)
     */
    public void hoodOn() {
        worker.execute(() -> {
            if (hoodOn) {
                Log.info("[%s] 시나리오: 후드가 이미 켜져 있음", site.gatewayId());
                return;
            }
            hoodOn = true;
            sendEvent("HOOD_ON");
            generator.setHoodInterlock(!vemcBroken);
            Log.info("[%s] 시나리오: 후드 켜짐 (%s)", site.gatewayId(), vemcBroken ? "VEMC 고장, 연동 안 됨" : "급기 올림");
        });
    }

    /**
     * 후드 꺼짐 시나리오: VEMC가 HOOD_OFF를 보내고 환기장치는 평소대로 돌아간다.
     *
     * @since 2026-10-24 (#7)
     */
    public void hoodOff() {
        worker.execute(() -> {
            if (!hoodOn) {
                Log.info("[%s] 시나리오: 후드가 이미 꺼져 있음", site.gatewayId());
                return;
            }
            hoodOn = false;
            sendEvent("HOOD_OFF");
            generator.setHoodInterlock(false);
            Log.info("[%s] 시나리오: 후드 꺼짐", site.gatewayId());
        });
    }

    /**
     * VEMC 고장·수리 시나리오. 고장이면 후드 이벤트는 보내지만 환기장치로 신호가 가지 않는다.
     *
     * @since 2026-10-24 (#7)
     */
    public void setVemcBroken(boolean broken) {
        worker.execute(() -> {
            vemcBroken = broken;
            // 고장이면 연동이 멈추고, 고치면 켜져 있는 후드에 맞춰 연동이 다시 일어난다.
            generator.setHoodInterlock(hoodOn && !broken);
            Log.info("[%s] 시나리오: VEMC %s", site.gatewayId(), broken ? "고장" : "수리");
        });
    }

    /** 이벤트를 버퍼에 넣고, 연결되어 있으면 바로 보낸다. 게이트웨이 스레드에서 부른다. */
    private void sendEvent(String type) {
        if (eventBuffer.add(DeviceEventMessage.of(site.vemcId(), type, OffsetDateTime.now(SEOUL)))) {
            Log.error("[%s] 이벤트 버퍼가 가득 차 가장 오래된 이벤트를 버림", site.gatewayId());
        }
        if (client.isConnected()) {
            flush(eventBuffer, eventTopic(), "이벤트");
        } else {
            Log.info("[%s] 연결 끊김, 이벤트를 버퍼에 쌓음 (%d개)", site.gatewayId(), eventBuffer.size());
        }
    }

    /**
     * 측정값 시나리오를 켜거나 끈다(필터 막힘 #4, CO2·풍량·바이패스 #5). 생성기는 게이트웨이 스레드에서만 쓰므로 그 스레드에 맡긴다.
     *
     * @param label  로그에 남길 시나리오 이름
     * @param change 생성기에 적용할 변경
     * @since 2026-10-24 (#5)
     */
    public void changeTelemetry(String label, Consumer<TelemetryGenerator> change) {
        worker.execute(() -> {
            change.accept(generator);
            Log.info("[%s] 시나리오: %s", site.gatewayId(), label);
        });
    }

    /**
     * 네트워크 끊김 시나리오: DISCONNECT 없이 소켓을 끊는다. 브로커는 비정상 종료로 보고 Last Will(offline)을 보낸다.
     * 켜져 있는 동안 측정값은 버퍼에 쌓이고, {@link #networkUp()} 전까지 다시 연결하지 않는다.
     *
     * @since 2026-10-24 (#6)
     */
    public void networkDown() {
        worker.execute(() -> {
            networkDown = true;
            try {
                client.disconnectForcibly(0, 1_000, false);
            } catch (MqttException e) {
                Log.error("[%s] 연결 끊기 실패: %s", site.gatewayId(), e.getMessage());
            }
            Log.info("[%s] 시나리오: 네트워크 끊김 (다시 연결하지 않음)", site.gatewayId());
        });
    }

    /**
     * 네트워크 복구 시나리오: 다시 연결하고, 쌓인 측정값을 보낸다.
     *
     * @since 2026-10-24 (#6)
     */
    public void networkUp() {
        worker.execute(() -> {
            networkDown = false;
            backoff.reset();
            Log.info("[%s] 시나리오: 네트워크 복구, 쌓인 측정값 %d개", site.gatewayId(), buffer.size());
            tryConnect();
        });
    }

    public SiteConfig site() {
        return site;
    }

    public void close() {
        worker.shutdownNow();
        try {
            if (client.isConnected()) {
                // 정상 종료(DISCONNECT)는 Last Will을 보내지 않으므로 offline을 직접 보고한다.
                publishStatus(offlineStatusJson());
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

    /** 연결 시도가 중간에 실패했을 때 반쯤 열린 연결을 정리한다. 실패해도 다음 시도에는 지장이 없다. */
    private void dropConnectionQuietly() {
        if (!client.isConnected()) {
            return;
        }
        try {
            client.disconnectForcibly(0, 1_000, false);
        } catch (MqttException e) {
            Log.error("[%s] 연결 정리 실패: %s", site.gatewayId(), e.getMessage());
        }
    }

    private String eventTopic() {
        return "jabjaje/" + site.siteId() + "/" + site.vemcId() + "/event";
    }

    private String statusTopic() {
        return "jabjaje/" + site.siteId() + "/" + site.gatewayId() + "/status";
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

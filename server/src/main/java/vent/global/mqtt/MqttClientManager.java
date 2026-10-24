package vent.global.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.util.MqttTopicValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 서버의 MQTT 연결 관리자.
 *
 * <p>앱이 뜰 때 브로커에 연결하고, 등록된 {@link MqttMessageHandler}들의 토픽을 QoS 1로 구독한다.
 * 세션을 유지(clean start = false)하므로 서버가 재시작되는 동안 온 메시지는 브로커가 보관했다가 넘겨준다(스펙 4.3).
 * 브로커가 아직 없어도 서버는 뜨고, 첫 연결은 백그라운드에서 재시도한다(ADR-0009).
 *
 * <p>수신 확인(PUBACK)은 직접 보낸다(manual ack, ADR-0012). 처리기가 {@link MqttHandleResult#DONE}을 돌려줄 때만 보내고,
 * {@link MqttHandleResult#RETRY_LATER}면 보내지 않은 채 잠시 뒤 다시 연결한다. MQTT는 확인을 못 받은 메시지를
 * 재연결 때 다시 보내므로, DB가 잠깐 죽어도 측정값이 사라지지 않는다(스펙 9.1 C15).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Component
public class MqttClientManager implements SmartLifecycle, MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);
    private static final int QOS_AT_LEAST_ONCE = 1;
    private static final long SESSION_EXPIRY_SECONDS = 24 * 60 * 60;
    private static final int KEEP_ALIVE_SECONDS = 30;
    /** 직접 재시도(첫 연결, 재전송용 재연결)와 Paho 자동 재연결 모두 이 간격을 넘지 않는다. */
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofSeconds(30);
    /** 처리 실패 뒤 다시 연결하기까지 기다리는 시간. 그 사이 DB가 회복할 시간을 준다. */
    private static final long REDELIVERY_DELAY_MILLIS = 5_000;

    private final MqttBrokerProperties properties;
    private final List<MqttMessageHandler> handlers;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "mqtt-connector"));
    private final AtomicBoolean redeliveryScheduled = new AtomicBoolean(false);

    private volatile MqttClient client;
    private volatile boolean running;
    private volatile boolean subscribed;

    public MqttClientManager(MqttBrokerProperties properties, List<MqttMessageHandler> handlers) {
        this.properties = properties;
        this.handlers = handlers;
    }

    @Override
    public void start() {
        try {
            client = new MqttClient(properties.serverUri(), properties.clientId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("잘못된 MQTT 설정: " + properties.serverUri(), e);
        }
        client.setCallback(this);
        client.setManualAcks(true);
        running = true;
        executor.execute(this::connectWithRetry);
    }

    /** {@code vent.mqtt.enabled=false}면 앱이 떠도 연결하지 않는다. */
    @Override
    public boolean isAutoStartup() {
        return properties.enabled();
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.warn("MQTT 연결 종료 중 오류", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 브로커에 연결되어 있고 모든 처리기의 토픽 구독을 마쳤는지. 이 전에 발행된 메시지는 서버가 받지 못할 수 있다. */
    public boolean isReady() {
        return subscribed && client != null && client.isConnected();
    }

    /** 연결될 때까지 간격을 늘려 가며 재시도한다. 연결이 갑자기 끊긴 뒤의 재연결은 Paho 자동 재연결이 맡는다. */
    private void connectWithRetry() {
        long delay = 1_000;
        while (running && !client.isConnected()) {
            try {
                client.connect(connectionOptions());
                return;
            } catch (MqttException e) {
                log.warn("MQTT 브로커 연결 실패({}), {}ms 뒤 재시도: {}", properties.serverUri(), delay, e.getMessage());
                try {
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delay = Math.min(delay * 2, MAX_RECONNECT_DELAY.toMillis());
            }
        }
    }

    private MqttConnectionOptions connectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(SESSION_EXPIRY_SECONDS);
        options.setAutomaticReconnect(true);
        // Paho 기본값은 재연결 간격이 최대 2분까지 늘어나 브로커 복구 뒤에도 수신이 늦게 재개된다.
        options.setAutomaticReconnectDelay(1, (int) MAX_RECONNECT_DELAY.toSeconds());
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        if (properties.hasCredentials()) {
            // 익명 접속은 브로커가 거부한다 (#13, 스펙 9.2 S1).
            options.setUserName(properties.username());
            options.setPassword(properties.password().getBytes(StandardCharsets.UTF_8));
        }
        return options;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        log.info("MQTT 연결됨: {} (재연결: {})", serverUri, reconnect);
        // 콜백 스레드에서 동기 호출을 하면 Paho가 막힐 수 있어 다른 스레드에서 구독한다.
        executor.execute(this::subscribeAll);
    }

    private void subscribeAll() {
        boolean allSubscribed = true;
        for (MqttMessageHandler handler : handlers) {
            try {
                client.subscribe(handler.topicFilter(), QOS_AT_LEAST_ONCE);
                log.info("MQTT 구독: {}", handler.topicFilter());
            } catch (MqttException e) {
                allSubscribed = false;
                log.error("MQTT 구독 실패: {}", handler.topicFilter(), e);
            }
        }
        subscribed = allSubscribed;
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        MqttHandleResult result = MqttHandleResult.DONE;
        for (MqttMessageHandler handler : handlers) {
            if (MqttTopicValidator.isMatched(handler.topicFilter(), topic)) {
                try {
                    if (handler.handle(topic, message.getPayload(), message.isRetained()) == MqttHandleResult.RETRY_LATER) {
                        result = MqttHandleResult.RETRY_LATER;
                    }
                } catch (RuntimeException e) {
                    // 예상하지 못한 예외는 다시 받아도 또 실패할 가능성이 커서(버그) 확인 응답을 보내고 버린다.
                    // 예외가 Paho까지 올라가면 연결이 끊긴다(ADR-0009).
                    log.error("MQTT 메시지 처리 중 예상하지 못한 오류, 버림: {}", topic, e);
                }
            }
        }

        if (result == MqttHandleResult.DONE) {
            acknowledge(message);
        } else {
            scheduleRedelivery(topic);
        }
    }

    private void acknowledge(MqttMessage message) {
        if (message.getQos() == 0) {
            return;
        }
        try {
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException e) {
            log.warn("MQTT 수신 확인 실패, 브로커가 다시 보낼 수 있음 (messageId {}): {}", message.getId(), e.getMessage());
        }
    }

    /**
     * 확인 응답을 보내지 않은 메시지를 브로커가 다시 보내게 한다. MQTT는 재연결 때만 다시 보내므로,
     * 잠시 기다린 뒤 연결을 끊고 같은 세션으로 다시 연결한다. 실패가 몰려도 재연결은 한 번만 예약한다.
     */
    private void scheduleRedelivery(String topic) {
        if (!redeliveryScheduled.compareAndSet(false, true)) {
            return;
        }
        log.warn("MQTT 메시지 처리 보류({}), {}ms 뒤 다시 연결해 재전송을 받음", topic, REDELIVERY_DELAY_MILLIS);
        executor.schedule(this::reconnectForRedelivery, REDELIVERY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void reconnectForRedelivery() {
        redeliveryScheduled.set(false);
        if (!client.isConnected()) {
            // 이미 끊겨 있으면 Paho 자동 재연결이 다시 붙을 때 브로커가 다시 보낸다. 여기서 connect를 부르면 둘이 부딪힌다.
            return;
        }
        try {
            subscribed = false;
            client.disconnect();
        } catch (MqttException e) {
            log.warn("재전송을 위한 연결 종료 실패, 다시 예약: {}", e.getMessage());
            if (client.isConnected()) {
                scheduleRedelivery("(재시도)");
                return;
            }
        }
        // 직접 끊은 연결은 Paho가 자동으로 다시 붙지 않으므로 직접 연결한다.
        connectWithRetry();
    }

    @Override
    public void disconnected(MqttDisconnectResponse response) {
        subscribed = false;
        log.warn("MQTT 연결 끊김: {}", response.getReasonString());
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        log.error("MQTT 오류", exception);
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        // 서버는 아직 발행하지 않는다. 원격 명령(#8)에서 쓴다.
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // 확장 인증은 쓰지 않는다.
    }
}

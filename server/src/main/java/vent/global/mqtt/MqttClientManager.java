package vent.global.mqtt;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
@Component
public class MqttClientManager implements SmartLifecycle, MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);
    private static final int QOS_AT_LEAST_ONCE = 1;
    private static final long SESSION_EXPIRY_SECONDS = 24 * 60 * 60;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000;

    private final MqttBrokerProperties properties;
    private final List<MqttMessageHandler> handlers;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "mqtt-connector"));

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
        running = true;
        executor.execute(this::connectWithRetry);
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
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

    /** 첫 연결이 될 때까지 간격을 늘려 가며 재시도한다. 연결된 뒤의 재연결은 Paho 자동 재연결이 맡는다. */
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
                delay = Math.min(delay * 2, MAX_RETRY_DELAY_MILLIS);
            }
        }
    }

    private MqttConnectionOptions connectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(SESSION_EXPIRY_SECONDS);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
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
        for (MqttMessageHandler handler : handlers) {
            if (MqttTopicValidator.isMatched(handler.topicFilter(), topic)) {
                try {
                    handler.handle(topic, message.getPayload());
                } catch (RuntimeException e) {
                    // 예외가 Paho까지 올라가면 연결이 끊긴다(ADR-0009).
                    log.error("MQTT 메시지 처리 실패: {}", topic, e);
                }
            }
        }
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

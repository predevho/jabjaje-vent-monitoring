package vent.global.mqtt;

/**
 * MQTT 메시지 처리기.
 *
 * <p>기능마다 이 인터페이스를 구현한 빈을 두면 {@link MqttClientManager}가 연결될 때 구독하고, 맞는 토픽의 메시지를 넘겨준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public interface MqttMessageHandler {

    /** 구독할 토픽 필터. {@code +}, {@code #} 와일드카드를 쓸 수 있다. */
    String topicFilter();

    /**
     * 메시지 하나를 처리한다. Paho 콜백 스레드에서 호출되므로 예외를 밖으로 던지지 않아야 한다.
     *
     * @param topic   실제로 받은 토픽
     * @param payload 메시지 본문
     */
    void handle(String topic, byte[] payload);
}
